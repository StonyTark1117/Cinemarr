package stonytark.cinemarr.client;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecHWConfig;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVBufferRef;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder_by_name;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_get_hw_config;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avformat.av_find_best_stream;
import static org.bytedeco.ffmpeg.global.avformat.av_find_input_format;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avformat.avio_alloc_context;
import static org.bytedeco.ffmpeg.global.avformat.avio_context_free;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_CUDA;
import static org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_D3D11VA;
import static org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_DXVA2;
import static org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_QSV;
import static org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_VAAPI;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.av_buffer_ref;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_unref;
import static org.bytedeco.ffmpeg.global.avutil.av_free;
import static org.bytedeco.ffmpeg.global.avutil.av_hwdevice_ctx_create;
import static org.bytedeco.ffmpeg.global.avutil.av_hwframe_transfer_data;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.avutil.av_strerror;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

/** Generic libavcodec hardware path. Hardware frames are downloaded and converted to CPU RGBA. */
final class FfmpegHardwareVideoDecoder {
    private static final int IO_BUFFER_BYTES = 32 * 1024;
    private static final long MAX_RETAINED_VIDEO_BYTES = 128L * 1024L * 1024L;
    private static final Map<String, AVBufferRef> DEVICES = new ConcurrentHashMap<>();

    /** Creates and retains one device per backend/device pair so playback startup can probe asynchronously. */
    static void probe(VideoDecoderBackend backend, String device) throws HardwareDecoderException {
        int type = deviceType(backend);
        AVCodec codec = decoder(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264, backend);
        if (codec == null || codec.isNull()) throw failure("find H.264 decoder", -1);
        hardwarePixelFormat(codec, type);
        deviceContext(backend, device, type);
    }

    static Result decode(byte[] mpegTs, VideoDecoderBackend backend, String device) throws HardwareDecoderException {
        int deviceType = deviceType(backend);
        long started = System.nanoTime();
        long transferNanos = 0L;
        long conversionNanos = 0L;
        FrameCollector frames = new FrameCollector(MAX_RETAINED_VIDEO_BYTES);
        Input input = new Input(mpegTs);
        AVFormatContext format = null;
        AVIOContext io = null;
        AVCodecContext codecContext = null;
        AVPacket packet = null;
        AVFrame hardwareFrame = null;
        AVFrame softwareFrame = null;
        BytePointer ioBuffer = null;
        AVCodecContext.Get_format_AVCodecContext_IntPointer formatSelector = null;
        try {
            ioBuffer = new BytePointer(av_malloc(IO_BUFFER_BYTES));
            if (ioBuffer.isNull()) throw failure("allocate FFmpeg input buffer", -1);
            io = avio_alloc_context(ioBuffer, IO_BUFFER_BYTES, 0, null, input, null, null);
            if (io == null || io.isNull()) throw failure("allocate FFmpeg input context", -1);
            format = avformat_alloc_context();
            if (format == null || format.isNull()) throw failure("allocate FFmpeg format context", -1);
            format.pb(io);
            check(avformat_open_input(format, "", av_find_input_format("mpegts"), null), "open MPEG-TS segment");
            check(avformat_find_stream_info(format, (org.bytedeco.ffmpeg.avutil.AVDictionary) null), "read MPEG-TS stream info");
            int streamIndex = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, (PointerPointer) null, 0);
            check(streamIndex, "find H.264 video stream");
            AVStream stream = format.streams(streamIndex);
            AVCodec codec = decoder(stream.codecpar().codec_id(), backend);
            if (codec == null || codec.isNull()) throw failure("find H.264 decoder", -1);
            int hardwarePixelFormat = hardwarePixelFormat(codec, deviceType);
            codecContext = avcodec_alloc_context3(codec);
            if (codecContext == null || codecContext.isNull()) throw failure("allocate video codec context", -1);
            check(avcodec_parameters_to_context(codecContext, stream.codecpar()), "copy video codec parameters");
            codecContext.hw_device_ctx(av_buffer_ref(deviceContext(backend, device, deviceType)));
            final int selectedFormat = hardwarePixelFormat;
            formatSelector = new AVCodecContext.Get_format_AVCodecContext_IntPointer() {
                @Override public int call(AVCodecContext ignored, IntPointer offered) {
                    for (int index = 0; index < 64; index++) {
                        int value = offered.get(index);
                        if (value == selectedFormat) return value;
                        if (value == AV_PIX_FMT_NONE) break;
                    }
                    return AV_PIX_FMT_NONE;
                }
            };
            codecContext.get_format(formatSelector);
            check(avcodec_open2(codecContext, codec, (org.bytedeco.ffmpeg.avutil.AVDictionary) null), "open hardware video decoder");
            packet = av_packet_alloc();
            hardwareFrame = av_frame_alloc();
            softwareFrame = av_frame_alloc();
            if (packet == null || hardwareFrame == null || softwareFrame == null) throw failure("allocate video frames", -1);

            int readResult;
            while ((readResult = av_read_frame(format, packet)) >= 0) {
                if (packet.stream_index() == streamIndex) {
                    check(avcodec_send_packet(codecContext, packet), "submit hardware video packet");
                    Timings timings = receive(codecContext, stream, hardwarePixelFormat, hardwareFrame, softwareFrame, frames);
                    transferNanos += timings.transferNanos;
                    conversionNanos += timings.conversionNanos;
                }
                av_packet_unref(packet);
            }
            if (readResult != AVERROR_EOF) check(readResult, "read hardware video packet");
            int flush = avcodec_send_packet(codecContext, null);
            if (flush < 0 && flush != AVERROR_EOF) check(flush, "flush hardware video decoder");
            Timings timings = receive(codecContext, stream, hardwarePixelFormat, hardwareFrame, softwareFrame, frames);
            transferNanos += timings.transferNanos;
            conversionNanos += timings.conversionNanos;
            if (frames.frames.isEmpty()) throw failure("decode hardware video frames", -1);
            return new Result(frames.frames, frames.dropped, System.nanoTime() - started, transferNanos,
                    conversionNanos, frames.peakBytes);
        } finally {
            if (packet != null) av_packet_free(packet);
            if (hardwareFrame != null) av_frame_free(hardwareFrame);
            if (softwareFrame != null) av_frame_free(softwareFrame);
            if (codecContext != null) avcodec_free_context(codecContext);
            if (formatSelector != null) formatSelector.close();
            if (format != null && !format.isNull()) avformat_close_input(format);
            if (io != null && !io.isNull()) {
                BytePointer activeBuffer = io.buffer();
                if (activeBuffer != null && !activeBuffer.isNull()) av_free(activeBuffer);
                io.buffer(null);
                avio_context_free(io);
            } else if (ioBuffer != null && !ioBuffer.isNull()) av_free(ioBuffer);
            // AVIOContext only keeps the native callback pointer. Closing it here also
            // keeps the Java callback strongly reachable until FFmpeg is finished.
            input.close();
        }
    }

    private static AVBufferRef deviceContext(VideoDecoderBackend backend, String device, int deviceType)
            throws HardwareDecoderException {
        String normalized = device == null ? "" : device.trim();
        String key = backend.configValue() + '\n' + normalized;
        AVBufferRef existing = DEVICES.get(key);
        if (existing != null && !existing.isNull()) return existing;
        synchronized (DEVICES) {
            existing = DEVICES.get(key);
            if (existing != null && !existing.isNull()) return existing;
            AVBufferRef created = new AVBufferRef((Pointer) null);
            int result = av_hwdevice_ctx_create(created, deviceType, normalized.isEmpty() ? null : normalized, null, 0);
            if (result < 0) {
                created.close();
                throw failure("create " + backend.configValue() + " device", result);
            }
            DEVICES.put(key, created);
            return created;
        }
    }

    private static Timings receive(AVCodecContext codec, AVStream stream, int hardwarePixelFormat,
                                   AVFrame hardware, AVFrame software, FrameCollector output)
            throws HardwareDecoderException {
        long transfer = 0L;
        long conversion = 0L;
        while (true) {
            int result = avcodec_receive_frame(codec, hardware);
            if (result == -11 || result == AVERROR_EOF) break; // AVERROR(EAGAIN) or normal drain completion.
            check(result, "receive hardware video frame");
            AVFrame source = hardware;
            if (hardware.format() == hardwarePixelFormat) {
                long before = System.nanoTime();
                check(av_hwframe_transfer_data(software, hardware, 0), "download hardware video frame");
                transfer += System.nanoTime() - before;
                software.best_effort_timestamp(hardware.best_effort_timestamp());
                source = software;
            }
            long before = System.nanoTime();
            output.add(toRgba(source, timestampUs(source.best_effort_timestamp(), stream)));
            conversion += System.nanoTime() - before;
            av_frame_unref(hardware);
            av_frame_unref(software);
        }
        return new Timings(transfer, conversion);
    }

    private static DecodedVideoFrame toRgba(AVFrame source, long timestampUs) throws HardwareDecoderException {
        int width = source.width();
        int height = source.height();
        if (width < 1 || height < 1) throw failure("read hardware frame dimensions", -1);
        long size = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (size > Integer.MAX_VALUE) throw failure("allocate RGBA video frame", -1);
        BytePointer pixels = new BytePointer(size);
        PointerPointer destination = new PointerPointer(4);
        IntPointer destinationStride = new IntPointer(4);
        destination.put(0, pixels);
        destinationStride.put(0, width * 4);
        SwsContext scaler = sws_getContext(width, height, source.format(), width, height, AV_PIX_FMT_RGBA,
                SWS_BILINEAR, null, null, (double[]) null);
        if (scaler == null || scaler.isNull()) {
            pixels.close(); destination.close(); destinationStride.close();
            throw failure("create RGBA converter", -1);
        }
        try {
            check(sws_scale(scaler, source.data(), source.linesize(), 0, height, destination, destinationStride),
                    "convert hardware frame to RGBA");
            byte[] rgba = new byte[(int) size];
            pixels.position(0).get(rgba);
            return new DecodedVideoFrame(timestampUs, width, height, rgba);
        } finally {
            sws_freeContext(scaler);
            pixels.close(); destination.close(); destinationStride.close();
        }
    }

    private static int hardwarePixelFormat(AVCodec codec, int deviceType) throws HardwareDecoderException {
        for (int index = 0; ; index++) {
            AVCodecHWConfig config = avcodec_get_hw_config(codec, index);
            if (config == null || config.isNull()) break;
            if (config.device_type() == deviceType && (config.methods() & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX) != 0) {
                return config.pix_fmt();
            }
        }
        throw failure("find compatible hardware pixel format", -1);
    }

    private static AVCodec decoder(int codecId, VideoDecoderBackend backend) {
        AVCodec codec = backend == VideoDecoderBackend.QSV ? avcodec_find_decoder_by_name("h264_qsv") : null;
        return codec == null || codec.isNull() ? avcodec_find_decoder(codecId) : codec;
    }

    private static long timestampUs(long timestamp, AVStream stream) {
        if (timestamp < 0 || stream.time_base().den() == 0) return 0L;
        return Math.max(0L, timestamp * 1_000_000L * stream.time_base().num() / stream.time_base().den());
    }

    private static int deviceType(VideoDecoderBackend backend) throws HardwareDecoderException {
        switch (backend) {
            case VAAPI: return AV_HWDEVICE_TYPE_VAAPI;
            case QSV: return AV_HWDEVICE_TYPE_QSV;
            case CUDA: return AV_HWDEVICE_TYPE_CUDA;
            case D3D11VA: return AV_HWDEVICE_TYPE_D3D11VA;
            case DXVA2: return AV_HWDEVICE_TYPE_DXVA2;
            default: throw failure("select a hardware decoder backend", -1);
        }
    }

    private static void check(int result, String operation) throws HardwareDecoderException {
        if (result < 0) throw failure(operation, result);
    }

    private static HardwareDecoderException failure(String operation, int code) {
        byte[] text = new byte[256];
        if (code < 0) av_strerror(code, text, text.length);
        int length = 0;
        while (length < text.length && text[length] != 0) length++;
        String detail = length == 0 ? "unsupported or unavailable" : new String(text, 0, length, java.nio.charset.StandardCharsets.UTF_8);
        return new HardwareDecoderException(operation + ": " + detail);
    }

    static final class Result {
        final List<DecodedVideoFrame> frames;
        final int droppedFrames;
        final long wallNanos;
        final long transferNanos;
        final long conversionNanos;
        final long peakRetainedBytes;
        Result(List<DecodedVideoFrame> frames, int droppedFrames, long wallNanos, long transferNanos,
               long conversionNanos, long peakRetainedBytes) {
            this.frames = frames; this.droppedFrames = droppedFrames; this.wallNanos = wallNanos;
            this.transferNanos = transferNanos; this.conversionNanos = conversionNanos;
            this.peakRetainedBytes = peakRetainedBytes;
        }
    }

    static final class HardwareDecoderException extends Exception {
        HardwareDecoderException(String message) { super(message); }
    }

    private static final class Timings {
        final long transferNanos;
        final long conversionNanos;
        Timings(long transferNanos, long conversionNanos) { this.transferNanos = transferNanos; this.conversionNanos = conversionNanos; }
    }

    private static final class FrameCollector {
        final long maximumBytes;
        final List<DecodedVideoFrame> frames = new ArrayList<>();
        long bytes;
        long peakBytes;
        int dropped;
        FrameCollector(long maximumBytes) { this.maximumBytes = maximumBytes; }
        void add(DecodedVideoFrame frame) {
            frames.add(frame);
            bytes += frame.rgbaView().length;
            peakBytes = Math.max(peakBytes, bytes);
            while (bytes > maximumBytes && frames.size() > 1) {
                int remove = frames.size() == 2 ? 0 : closestInteriorFrame();
                bytes -= frames.remove(remove).rgbaView().length;
                dropped++;
            }
        }
        int closestInteriorFrame() {
            int result = 1;
            long smallestSpan = Long.MAX_VALUE;
            for (int index = 1; index + 1 < frames.size(); index++) {
                long span = frames.get(index + 1).presentationTimeUs() - frames.get(index - 1).presentationTimeUs();
                if (span < smallestSpan) { smallestSpan = span; result = index; }
            }
            return result;
        }
    }

    private static final class Input extends Read_packet_Pointer_BytePointer_int {
        private final byte[] bytes;
        private int position;
        Input(byte[] bytes) { this.bytes = bytes; }
        @Override public int call(Pointer ignored, BytePointer destination, int requested) {
            if (position >= bytes.length) return AVERROR_EOF;
            int count = Math.min(requested, bytes.length - position);
            destination.put(bytes, position, count);
            position += count;
            return count;
        }
    }
}
