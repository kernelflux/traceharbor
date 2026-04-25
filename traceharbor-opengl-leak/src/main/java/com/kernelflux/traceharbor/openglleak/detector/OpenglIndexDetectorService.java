package com.kernelflux.traceharbor.openglleak.detector;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import com.kernelflux.traceharbor.openglleak.comm.FuncNameString;
import com.kernelflux.traceharbor.openglleak.hook.OpenGLHook;
import com.kernelflux.traceharbor.openglleak.utils.EGLHelper;
import com.kernelflux.traceharbor.util.TraceHarborLog;

import java.util.HashMap;
import java.util.Map;

public class OpenglIndexDetectorService extends Service {

    private final static String TAG = "traceharbor.OpenglIndexDetectorService";

    private final IOpenglIndexDetector.Stub stub = new IOpenglIndexDetector.Stub() {
        @Override
        public Map<String, Integer> seekIndex() throws RemoteException {
            return seekOpenglFuncIndex();
        }

        @Override
        public void destory() throws RemoteException {
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }

    private Map<String, Integer> seekOpenglFuncIndex() {
        // 初始化 egl 环境，目的为了初始化 gl 表
        EGLHelper.initOpenGL();
        OpenGLHook.getInstance().init();
        TraceHarborLog.i(TAG, "init env succ");

        int glGenTexturesIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_TEXTURES);
        TraceHarborLog.i(TAG, "glGenTextures index:" + glGenTexturesIndex);
        int glDeleteTexturesIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_TEXTURES);
        TraceHarborLog.i(TAG, "glDeleteTextures index:" + glDeleteTexturesIndex);

        int glGenBuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_BUFFERS);
        TraceHarborLog.i(TAG, "glGenBuffers index:" + glGenBuffersIndex);
        int glDeleteBuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_BUFFERS);
        TraceHarborLog.i(TAG, "glDeleteBuffers index:" + glDeleteBuffersIndex);

        int glGenFramebuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_FRAMEBUFFERS);
        TraceHarborLog.i(TAG, "glGenFramebuffers index:" + glGenFramebuffersIndex);
        int glDeleteFramebuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_FRAMEBUFFERS);
        TraceHarborLog.i(TAG, "glDeleteFramebuffers index:" + glDeleteFramebuffersIndex);

        int glGenRenderbuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_RENDERBUFFERS);
        TraceHarborLog.i(TAG, "glGenRenderbuffers index:" + glGenRenderbuffersIndex);
        int glDeleteRenderbuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_RENDERBUFFERS);
        TraceHarborLog.i(TAG, "glDeleteRenderbuffers index:" + glDeleteRenderbuffersIndex);

        int glTexImage2DIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_TEX_IMAGE_2D);
        TraceHarborLog.i(TAG, "glTexImage2DIndex index:" + glTexImage2DIndex);
        int glTexImage3DIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_TEX_IMAGE_3D);
        TraceHarborLog.i(TAG, "glTexImage3DIndex index:" + glTexImage3DIndex);

        int glBindTextureIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_TEXTURE);
        TraceHarborLog.i(TAG, "glBindTextureIndex index:" + glBindTextureIndex);
        int glBindBufferIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_BUFFER);
        TraceHarborLog.i(TAG, "glBindBufferIndex index:" + glBindBufferIndex);
        int glBindFramebufferIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_FRAMEBUFFER);
        TraceHarborLog.i(TAG, "glBindFramebufferIndex index:" + glBindFramebufferIndex);
        int glBindRenderbufferIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_RENDERBUFFER);
        TraceHarborLog.i(TAG, "glBindRenderbufferIndex index:" + glBindRenderbufferIndex);

        int glBufferDataIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BUFFER_DATA);
        TraceHarborLog.i(TAG, "glBufferData index:" + glBufferDataIndex);
        int glRenderbufferStorageIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_RENDER_BUFFER_STORAGE);
        TraceHarborLog.i(TAG, "glRenderbufferStorage index:" + glRenderbufferStorageIndex);

        if ((glGenTexturesIndex * glDeleteTexturesIndex * glGenBuffersIndex * glDeleteBuffersIndex * glGenFramebuffersIndex
                * glDeleteFramebuffersIndex * glGenRenderbuffersIndex * glDeleteRenderbuffersIndex * glTexImage2DIndex * glTexImage3DIndex
                * glBindTextureIndex * glBindBufferIndex * glBindFramebufferIndex * glBindRenderbufferIndex
                * glRenderbufferStorageIndex * glBufferDataIndex) == 0) {
            TraceHarborLog.e(TAG, "seek func index fail!");
            return null;
        }

        Map<String, Integer> out = new HashMap<>();
        out.put(FuncNameString.GL_GEN_TEXTURES, glGenTexturesIndex);
        out.put(FuncNameString.GL_DELETE_TEXTURES, glDeleteTexturesIndex);
        out.put(FuncNameString.GL_GEN_BUFFERS, glGenBuffersIndex);
        out.put(FuncNameString.GL_DELETE_BUFFERS, glDeleteBuffersIndex);
        out.put(FuncNameString.GL_GEN_FRAMEBUFFERS, glGenFramebuffersIndex);
        out.put(FuncNameString.GL_DELETE_FRAMEBUFFERS, glDeleteFramebuffersIndex);
        out.put(FuncNameString.GL_GEN_RENDERBUFFERS, glGenRenderbuffersIndex);
        out.put(FuncNameString.GL_DELETE_RENDERBUFFERS, glDeleteRenderbuffersIndex);
        out.put(FuncNameString.GL_TEX_IMAGE_2D, glTexImage2DIndex);
        out.put(FuncNameString.GL_TEX_IMAGE_3D, glTexImage3DIndex);
        out.put(FuncNameString.GL_BIND_TEXTURE, glBindTextureIndex);
        out.put(FuncNameString.GL_BIND_BUFFER, glBindBufferIndex);
        out.put(FuncNameString.GL_BIND_FRAMEBUFFER, glBindFramebufferIndex);
        out.put(FuncNameString.GL_BIND_RENDERBUFFER, glBindRenderbufferIndex);
        out.put(FuncNameString.GL_BUFFER_DATA, glBufferDataIndex);
        out.put(FuncNameString.GL_RENDER_BUFFER_STORAGE, glRenderbufferStorageIndex);

        TraceHarborLog.i(TAG, "seek func index succ!");
        return out;
    }

}
