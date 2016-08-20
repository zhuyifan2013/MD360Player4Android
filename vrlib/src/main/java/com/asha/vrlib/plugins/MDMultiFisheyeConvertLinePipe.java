package com.asha.vrlib.plugins;

import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLES20;

import com.asha.vrlib.MD360Director;
import com.asha.vrlib.MD360DirectorFactory;
import com.asha.vrlib.MD360Program;
import com.asha.vrlib.objects.MDAbsObject3D;
import com.asha.vrlib.objects.MDObject3DHelper;
import com.asha.vrlib.texture.MD360Texture;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static com.asha.vrlib.common.GLUtil.glCheck;

/**
 * Created by hzqiujiadi on 16/8/7.
 * hzqiujiadi ashqalcn@gmail.com
 *
 *
 */
public class MDMultiFisheyeConvertLinePipe extends MDAbsLinePipe {

    private MDMesh object3D;

    private MD360Director mDirector;

    private int mFrameBufferId;

    private int mTextureId;

    private int mRenderBufferId;

    private Rect mViewport = new Rect();

    public MDMultiFisheyeConvertLinePipe() {
        mDirector = new MD360DirectorFactory.OrthogonalImpl().createDirector(0);
        object3D = new MDMesh();
    }

    @Override
    public void init(final Context context) {
        MDObject3DHelper.loadObj(context,object3D);
    }

    private void createFrameBuffer(int width, int height){

        if (this.mTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[] { this.mTextureId }, 0);
        }
        if (this.mRenderBufferId != 0) {
            GLES20.glDeleteRenderbuffers(1, new int[] { this.mRenderBufferId }, 0);
        }
        if (this.mFrameBufferId != 0) {
            GLES20.glDeleteFramebuffers(1, new int[] { this.mFrameBufferId }, 0);
        }

        // frame buffer
        int[] frameBuffer = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0]);
        mFrameBufferId = frameBuffer[0];
        glCheck("Multi Fish Eye frame buffer");

        // renderer buffer
        final int[] renderbufferIds = { 0 };
        GLES20.glGenRenderbuffers(1, renderbufferIds, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderbufferIds[0]);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);
        mRenderBufferId = renderbufferIds[0];
        glCheck("Multi Fish Eye renderer buffer");

        final int[] textureIds = { 0 };
        GLES20.glGenTextures(1, textureIds, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, (Buffer)null);
        mTextureId = textureIds[0];
        glCheck("Multi Fish Eye texture");

        // attach
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTextureId, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, renderbufferIds[0]);
        glCheck("Multi Fish Eye attach");

        // check
        final int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            final String s = "Framebuffer is not complete: ";
            final String value = String.valueOf(Integer.toHexString(status));
            throw new RuntimeException((value.length() != 0) ? s.concat(value) : s);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        glCheck("Multi Fish Eye attach");
    }

    @Override
    public void takeOver(int totalWidth, int totalHeight, int size) {
        mDirector.updateViewport(totalWidth, totalHeight);

        if (mViewport.width() != totalWidth || mViewport.height() != totalHeight){
            createFrameBuffer(totalWidth, totalHeight);
            mViewport.set(0,0, totalWidth, totalHeight);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, this.mFrameBufferId);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        glCheck("MDMultiFisheyeConvertLinePipe glClear");

    }

    public void draw(MD360Program program, MD360Texture texture, int width, int height){
        // Set our per-vertex lighting program.
        glCheck("MDMultiFisheyeConvertLinePipe mProgram use");

        int itemWidth = width / 2;
        int itemHeight = height;
        for (int index = 0; index < 2; index++){
            GLES20.glViewport(itemWidth * index, 0, itemWidth, itemHeight);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(itemWidth * index, 0, itemWidth, itemHeight);

            program.use();

            texture.texture(program);

            mDirector.updateViewport(itemWidth, itemHeight);
            object3D.uploadVerticesBufferIfNeed(program, index);
            object3D.uploadTexCoordinateBufferIfNeed(program, index);

            // Pass in the combined matrix.
            mDirector.shot(program);

            // GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            // GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);

            object3D.draw();

            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }

    }

    @Override
    public void commit(int totalWidth, int totalHeight, int index){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public int getTextureId() {
        return mTextureId;
    }

    private class MDMesh extends MDAbsObject3D {

        private static final String TAG = "MDMesh";

        public MDMesh() {
        }

        @Override
        protected void executeLoad(Context context) {
            generateMesh(this);
        }

        private void generateMesh(MDAbsObject3D object3D){
            final float PI = (float) Math.PI;
            int rows = 16;
            int columns = 16;
            int numPoint = (rows + 1) * (columns + 1);
            short r, s;
            float z = -8;
            float R = 1f/(float) rows;
            float S = 1f/(float) columns;

            float[] vertexs = new float[numPoint * 3];
            float[] texcoords = new float[numPoint * 2];
            float[] texcoords2 = new float[numPoint * 2];
            short[] indices = new short[numPoint * 6];



            int t = 0;
            int v = 0;
            for(r = 0; r < rows + 1; r++) {
                for(s = 0; s < columns + 1; s++) {

                    vertexs[v++] = (s * S * 2 - 1);
                    vertexs[v++] = (r * R * 2 - 1);
                    vertexs[v++] = z;

                    float FOV = 3.141592654f; // FOV of the fisheye, eg: 180 degrees
                    float width = 1;
                    float height = 1;

                    float theta = PI * (s * S - 0.5f); // -pi to pi
                    float phi = PI * (r * R - 0.5f);  // -pi/2 to pi/2

                    float psphx = (float) (Math.cos(phi) * Math.sin(theta));
                    float psphy = (float) (Math.cos(phi) * Math.cos(theta));
                    float psphz = (float) Math.sin(phi);

                    theta = (float) Math.atan2(psphz, psphx);
                    phi = (float) Math.atan2(Math.sqrt(psphx*psphx + psphz*psphz), psphy);
                    float rr = 1 * phi / FOV;

                    float a = (float) (0.5f * width + rr * Math.cos(theta));
                    float b = (float) (0.5f * height + rr * Math.sin(theta));

                    texcoords[t*2] = a * 0.5f;
                    texcoords[t*2 + 1] = b;

                    texcoords2[t*2] = a * 0.5f + 0.5f;
                    texcoords2[t*2 + 1] = b;

                    t++;

                }
            }



            int counter = 0;
            int sectorsPlusOne = columns + 1;
            for(r = 0; r < rows; r++){
                for(s = 0; s < columns; s++) {
                    short k0 = (short) ((r) * sectorsPlusOne + (s+1));  // (c)
                    short k1 = (short) ((r+1) * sectorsPlusOne + (s));    //(b)
                    short k2 = (short) (r * sectorsPlusOne + s);       //(a);
                    short k3 = (short) ((r) * sectorsPlusOne + (s+1));  // (c)
                    short k4 = (short) ((r+1) * sectorsPlusOne + (s+1));  // (d)
                    short k5 = (short) ((r+1) * sectorsPlusOne + (s));    //(b)

                    indices[counter++] = k0;
                    indices[counter++] = k1;
                    indices[counter++] = k2;
                    indices[counter++] = k3;
                    indices[counter++] = k4;
                    indices[counter++] = k5;
                }
            }

            // initialize vertex byte buffer for shape coordinates
            ByteBuffer bb = ByteBuffer.allocateDirect(
                    // (# of coordinate values * 4 bytes per float)
                    vertexs.length * 4);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertexs);
            vertexBuffer.position(0);

            // initialize vertex byte buffer for shape coordinates
            ByteBuffer ee = ByteBuffer.allocateDirect(
                    texcoords.length * 4);
            ee.order(ByteOrder.nativeOrder());
            FloatBuffer texBuffer = ee.asFloatBuffer();
            texBuffer.put(texcoords);
            texBuffer.position(0);

            // initialize vertex byte buffer for shape coordinates
            ByteBuffer ee2 = ByteBuffer.allocateDirect(
                    texcoords2.length * 4);
            ee2.order(ByteOrder.nativeOrder());
            FloatBuffer texBuffer2 = ee2.asFloatBuffer();
            texBuffer2.put(texcoords2);
            texBuffer2.position(0);

            // initialize byte buffer for the draw list
            ByteBuffer dlb = ByteBuffer.allocateDirect(
                    // (# of coordinate values * 2 bytes per short)
                    indices.length * 2);
            dlb.order(ByteOrder.nativeOrder());
            ShortBuffer indexBuffer = dlb.asShortBuffer();
            indexBuffer.put(indices);
            indexBuffer.position(0);

            object3D.setIndicesBuffer(indexBuffer);
            object3D.setTexCoordinateBuffer(0,texBuffer);
            object3D.setTexCoordinateBuffer(1,texBuffer2);
            object3D.setVerticesBuffer(0,vertexBuffer);
            object3D.setVerticesBuffer(1,vertexBuffer);
            object3D.setNumIndices(indices.length);
        }
    }

}
