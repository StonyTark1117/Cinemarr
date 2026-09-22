package stonytark.cinemarr.core.client;

import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

/** One opt-in, render-thread source receipt for an independent framebuffer oracle. */
public final class DisplayFrameCapture {
    private static final AtomicReference<Request> pending = new AtomicReference<Request>();
    private static final AtomicReference<Long> suppressed = new AtomicReference<Long>();
    private static final class Request {
        final long controller; final String nonce;
        Request(long controller, String nonce) { this.controller=controller; this.nonce=nonce; }
    }
    public static void request(String operation) {
        if (!ProtocolLimits.displayProbeEnabled()) return;
        String[] fields=operation.split(":",-1);
        if(fields.length!=4 || !fields[0].equals("video") || !fields[1].equals("display-frame")
                || !fields[3].matches("[0-9]{1,24}")) throw new IllegalArgumentException("Invalid frame receipt request");
        pending.set(new Request(Long.parseLong(fields[2]),fields[3]));
    }
    public static boolean requested(long controller) {
        Request value=pending.get();
        return ProtocolLimits.displayProbeEnabled() && value!=null && value.controller==controller;
    }
    public static void background(String operation) {
        if(!ProtocolLimits.displayProbeEnabled())return;
        String[] fields=operation.split(":",-1);
        if(fields.length!=4||!fields[0].equals("video")||!fields[1].equals("display-background")
                ||!(fields[3].equals("true")||fields[3].equals("false")))
            throw new IllegalArgumentException("Invalid background request");
        long controller=Long.parseLong(fields[2]);
        if(fields[3].equals("true"))suppressed.set(controller);
        else { Long current=suppressed.get();if(current!=null&&current.longValue()==controller)suppressed.compareAndSet(current,null); }
    }
    public static boolean isSuppressed(long controller) {
        Long value=suppressed.get();
        return ProtocolLimits.displayProbeEnabled()&&value!=null&&value.longValue()==controller;
    }
    public static void reset() { pending.set(null);suppressed.set(null); }
    public static String capture(VideoPackets.SessionState state, byte[] source, int width, int height,
                                 long retainedBytes, int derivedTextures, double[] camera) {
        Request value=pending.get();
        if(!requested(state.controllerPos()) || source==null || camera.length!=9
                || !pending.compareAndSet(value,null)) return "";
        try {
            if(ProtocolLimits.audioControlFile().isEmpty())throw new IllegalStateException("Missing acceptance control path");
            Path control=Paths.get(ProtocolLimits.audioControlFile()).toAbsolutePath();
            Path base=control.resolveSibling(control.getFileName()+".frame-"+value.nonce);
            StringBuilder mask=new StringBuilder();
            for(byte b:state.visibilityMask())mask.append(String.format("%02x",b&255));
            String metadata="{\"schema\":1,\"nonce\":\""+value.nonce+"\",\"controller\":"+state.controllerPos()
                    +",\"sourceWidth\":"+width+",\"sourceHeight\":"+height+",\"sourceSha256\":\""+Hashing.sha256(source)
                    +"\",\"retainedBytes\":"+retainedBytes+",\"derivedTextures\":"+derivedTextures
                    +",\"width\":"+state.screenWidth()+",\"height\":"+state.screenHeight()+",\"mask\":\""+mask
                    +"\",\"layout\":\""+state.presentationMode()+"\",\"mapping\":\""+state.displaySettings().mapping()
                    +"\",\"minimumU\":"+state.minimumU()+",\"minimumV\":"+state.minimumV()+",\"plane\":"+state.screenPlane()
                    +",\"facing\":\""+state.screenFacing()+"\",\"camera\":["+camera[0]+","+camera[1]+","+camera[2]
                    +","+camera[3]+","+camera[4]+","+camera[5]+"],\"feetY\":"+camera[6]+",\"guiOpen\":"+(camera[7]!=0)+",\"hudHidden\":"+(camera[8]!=0)
                    +",\"videoSuppressed\":"+isSuppressed(state.controllerPos())+"}";
            Files.write(Paths.get(base+".rgba"),source,StandardOpenOption.CREATE_NEW);
            // Metadata is published last; it never certifies a partial source file.
            Files.write(Paths.get(base+".json"),metadata.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
            return "request="+value.nonce+" complete=true";
        } catch(Exception failure) { return "request="+value.nonce+" complete=false error="+failure.getClass().getSimpleName(); }
    }
    private DisplayFrameCapture() {}
}
