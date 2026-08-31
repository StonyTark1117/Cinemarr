# Third-party notices

Cinemarr embeds the FFmpeg facade classes from `org.bytedeco:javacv:1.5.14` and the unmodified `org.bytedeco:javacpp:1.5.14` JARs, licensed under the Apache License 2.0. Only JavaCV's Frame, FrameGrabber, FFmpegFrameGrabber, and FFmpegLogCallback class families are included; optional capture backends are omitted. The complete Apache License 2.0 text is included as `LICENSE-Apache-2.0.txt`.

Cinemarr embeds the default, non-GPL `org.bytedeco:ffmpeg:8.1.2-1.5.14` JavaCPP Presets build. FFmpeg is licensed primarily under the GNU Lesser General Public License 2.1 or later. Cinemarr intentionally excludes the separately published `-gpl` artifacts and packages native binaries only for Linux x86-64, Linux ARM64, and Windows x86-64.
