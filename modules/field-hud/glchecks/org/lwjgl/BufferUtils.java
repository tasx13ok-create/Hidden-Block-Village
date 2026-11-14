package org.lwjgl;
/** Original direct-buffer test factory; matches the public LWJGL2 allocation contract. */
public final class BufferUtils {public static java.nio.FloatBuffer createFloatBuffer(int n){return java.nio.ByteBuffer.allocateDirect(n*4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();}}
