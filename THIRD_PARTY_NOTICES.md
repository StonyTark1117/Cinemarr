# Third-party notices

Cinemarr embeds `javazoom:jlayer:1.0.1`, the JavaZoom MP3 decoder/player/converter.

JLayer is distributed under the GNU Lesser General Public License. The upstream artifact identifies its license at <https://www.gnu.org/licenses/lgpl-2.1.html> and its project at <http://www.javazoom.net/javalayer/javalayer.html>. Cinemarr uses the unmodified JLayer JAR as a separable jar-in-jar dependency; its source artifact is available from Maven Central under the same coordinates. The complete license text is included as `LICENSE-LGPL-2.1-or-later.txt`.

Cinemarr also embeds `de.sciss:jump3r:1.0.5`, the Java Unofficial MP3 Encoder, a Java port of LAME.

Jump3r is distributed under the GNU Lesser General Public License, version 2.1 or later. Its project is at <https://git.iem.at/sciss/jump3r>. Cinemarr uses the unmodified Jump3r JAR as a separable jar-in-jar dependency; its source artifact is available from Maven Central under the same coordinates. The complete license text is included as `LICENSE-LGPL-2.1-or-later.txt`.

Cinemarr embeds the FFmpeg facade classes from `org.bytedeco:javacv:1.5.14` and the unmodified `org.bytedeco:javacpp:1.5.14` JARs, licensed under the Apache License 2.0. Only JavaCV's Frame, FrameGrabber, FFmpegFrameGrabber, and FFmpegLogCallback class families are included; optional capture backends are omitted. The complete Apache License 2.0 text is included as `LICENSE-Apache-2.0.txt`.

Cinemarr embeds the default, non-GPL `org.bytedeco:ffmpeg:8.1.2-1.5.14` JavaCPP Presets build. FFmpeg is licensed primarily under the GNU Lesser General Public License 2.1 or later. Cinemarr intentionally excludes the separately published `-gpl` artifacts and packages native binaries only for Linux x86-64, Linux ARM64, and Windows x86-64.
