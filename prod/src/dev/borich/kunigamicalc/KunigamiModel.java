package dev.borich.kunigamicalc;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

public class KunigamiModel {
    public static final int FLAG_FACE        = 1;
    public static final int FLAG_HAIR        = 2;
    public static final int FLAG_EYE_EGO     = 4;
    public static final int FLAG_ALPHA_BLEND = 8;
    public static final int FLAG_NO_OUTLINE  = 16;

    public static class Submesh {
        public String colTexName;
        public String sdwTexName;
        public int colTexId;
        public int sdwTexId;
        public int flags;
        public int vertexCount;
        public int indexCount;
        public int vboId;
        public int iboId;
    }

    public int numBones;
    public int numPoses;
    public int[] parentIdx;
    public float[][] l0Matrices;
    public float[][] ibmMatrices;
    public float[][] worldPose;
    public float[] skinMatrices;

    public int idxSpine1, idxSpine2, idxChest, idxNeck, idxHead;
    public int idxJaw, idxEyelid, idxEyeL, idxEyeR;
    public int idxHairF, idxHairT, idxHairB;

    public Map<String, Integer> poseNameMap = new HashMap<>();
    public float[][][] poseQuats;

    private float[][] qCurrent;
    private float[][] qStart;
    private float[][] qTarget;
    private float animTime = 1.0f;
    private float animDuration = 0.26f;
    private boolean returnToIdleScheduled = false;
    private float returnToIdleCountdown = 0.0f;

    // Special Easter Egg 67 dance state
    private float sixSevenDanceRemaining = 0.0f;
    private float sixSevenStepTimer = 0.0f;
    private boolean sixSevenToggle = false;

    private float totalTime = 0.0f;
    private float speechTimeRemaining = 0.0f;
    private float speechTotalDuration = 0.0f;
    private float headLookYaw = 0.0f;
    private float headLookPitch = 0.0f;
    private float targetLookYaw = 0.0f;
    private float targetLookPitch = 0.0f;
    private float hairVelX = 0.0f;
    private float hairDispX = 0.0f;
    private float prevHeadYaw = 0.0f;
    private boolean egoEyesActive = false;
    private float egoPulseTime = 0.0f;

    // Natural eyelid blinking & eye micro-gaze state
    private float blinkTimer = 2.4f;
    private float blinkProgress = -1.0f;
    private static final float BLINK_DURATION = 0.15f;

    public Submesh[] submeshes;
    private final Map<String, Integer> textureCache = new HashMap<>();

    private final float[] tmpRotMat = new float[16];
    private final float[] tmpMat = new float[16];
    private final float[] tmpQuat = new float[4];
    private final float[] procQuat = new float[4];

    public void load(Context context) throws IOException {
        textureCache.clear();
        AssetManager assets = context.getAssets();
        try (InputStream raw = assets.open("models/kunigami.k3d");
             DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 65536))) {

            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'K' || magic[1] != '3' || magic[2] != 'D' || magic[3] != '1') {
                throw new IOException("Invalid K3D1 header");
            }

            numBones = readIntLE(in);
            numPoses = readIntLE(in);
            int numSubmeshes = readIntLE(in);

            idxSpine1 = readIntLE(in);
            idxSpine2 = readIntLE(in);
            idxChest  = readIntLE(in);
            idxNeck   = readIntLE(in);
            idxHead   = readIntLE(in);
            idxJaw    = readIntLE(in);
            idxEyelid = readIntLE(in);
            idxEyeL   = readIntLE(in);
            idxEyeR   = readIntLE(in);
            idxHairF  = readIntLE(in);
            idxHairT  = readIntLE(in);
            idxHairB  = readIntLE(in);

            parentIdx = new int[numBones];
            l0Matrices = new float[numBones][16];
            ibmMatrices = new float[numBones][16];
            worldPose = new float[numBones][16];
            skinMatrices = new float[numBones * 16];

            for (int b = 0; b < numBones; b++) {
                parentIdx[b] = readIntLE(in);
                for (int i = 0; i < 16; i++) l0Matrices[b][i] = readFloatLE(in);
                for (int i = 0; i < 16; i++) ibmMatrices[b][i] = readFloatLE(in);
            }

            poseQuats = new float[numPoses][numBones][4];
            for (int p = 0; p < numPoses; p++) {
                String pname = readStringLE(in);
                poseNameMap.put(pname, p);
                for (int b = 0; b < numBones; b++) {
                    poseQuats[p][b][0] = readFloatLE(in);
                    poseQuats[p][b][1] = readFloatLE(in);
                    poseQuats[p][b][2] = readFloatLE(in);
                    poseQuats[p][b][3] = readFloatLE(in);
                }
            }

            qCurrent = new float[numBones][4];
            qStart   = new float[numBones][4];
            qTarget  = new float[numBones][4];
            int idleIdx = poseNameMap.getOrDefault("IDLE", 0);
            for (int b = 0; b < numBones; b++) {
                System.arraycopy(poseQuats[idleIdx][b], 0, qCurrent[b], 0, 4);
                System.arraycopy(poseQuats[idleIdx][b], 0, qStart[b], 0, 4);
                System.arraycopy(poseQuats[idleIdx][b], 0, qTarget[b], 0, 4);
            }

            submeshes = new Submesh[numSubmeshes];
            for (int s = 0; s < numSubmeshes; s++) {
                Submesh sm = new Submesh();
                sm.colTexName = readStringLE(in);
                sm.sdwTexName = readStringLE(in);
                sm.flags = readIntLE(in);
                sm.vertexCount = readIntLE(in);
                sm.indexCount = readIntLE(in);

                ByteBuffer vBuf = ByteBuffer.allocateDirect(sm.vertexCount * 16 * 4).order(ByteOrder.nativeOrder());
                FloatBuffer fBuf = vBuf.asFloatBuffer();

                byte[] rawVertBytes = new byte[sm.vertexCount * 52];
                in.readFully(rawVertBytes);
                ByteBuffer rv = ByteBuffer.wrap(rawVertBytes).order(ByteOrder.LITTLE_ENDIAN);

                for (int v = 0; v < sm.vertexCount; v++) {
                    for (int k = 0; k < 8; k++) fBuf.put(rv.getFloat());
                    float j0 = rv.get() & 0xFF;
                    float j1 = rv.get() & 0xFF;
                    float j2 = rv.get() & 0xFF;
                    float j3 = rv.get() & 0xFF;
                    fBuf.put(j0).put(j1).put(j2).put(j3);
                    for (int k = 0; k < 4; k++) fBuf.put(rv.getFloat());
                }
                fBuf.position(0);

                byte[] rawIdxBytes = new byte[sm.indexCount * 2];
                in.readFully(rawIdxBytes);
                ByteBuffer iBuf = ByteBuffer.allocateDirect(sm.indexCount * 2).order(ByteOrder.nativeOrder());
                ShortBuffer sBuf = iBuf.asShortBuffer();
                ByteBuffer ri = ByteBuffer.wrap(rawIdxBytes).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < sm.indexCount; i++) {
                    sBuf.put(ri.getShort());
                }
                sBuf.position(0);

                int[] bufs = new int[2];
                GLES30.glGenBuffers(2, bufs, 0);
                sm.vboId = bufs[0];
                sm.iboId = bufs[1];

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sm.vboId);
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, sm.vertexCount * 16 * 4, fBuf, GLES30.GL_STATIC_DRAW);

                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, sm.iboId);
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, sm.indexCount * 2, sBuf, GLES30.GL_STATIC_DRAW);

                sm.colTexId = loadTexture(assets, sm.colTexName);
                sm.sdwTexId = loadTexture(assets, sm.sdwTexName);
                submeshes[s] = sm;
            }

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
        updateSkeleton(0.016f);
    }

    private int loadTexture(AssetManager assets, String texName) throws IOException {
        Integer cached = textureCache.get(texName);
        if (cached != null) return cached;

        int[] texIds = new int[1];
        GLES30.glGenTextures(1, texIds, 0);
        int texId = texIds[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        try (InputStream is = assets.open("textures/" + texName)) {
            Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
            if (bmp != null) {
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
                bmp.recycle();
            }
        }
        textureCache.put(texName, texId);
        return texId;
    }

    public synchronized void triggerSixSevenDance() {
        sixSevenDanceRemaining = 2.8f;
        sixSevenStepTimer = 0.0f;
        sixSevenToggle = false;
        egoPulseTime = 3.2f;
        setTargetPoseInternal("EASTER_67_A", 0.20f, 1.8f, -6.0f, 3.0f, 3.2f);
    }

    public synchronized void triggerEasterEggPose(String poseName, float egoDurationSec, float speechSec) {
        sixSevenDanceRemaining = 0.0f;
        if (egoDurationSec > 0.0f) {
            egoPulseTime = egoDurationSec;
        }
        setTargetPoseInternal(poseName, 0.25f, speechSec, 0.0f, 3.0f, 3.8f);
    }

    public synchronized void triggerPose(String poseName, float durationSec, float speechSec, float lookYawDeg, float lookPitchDeg) {
        sixSevenDanceRemaining = 0.0f;
        if ("EQUALS_EGO".equals(poseName)) {
            egoPulseTime = 4.2f;
        }
        setTargetPoseInternal(poseName, durationSec, speechSec, lookYawDeg, lookPitchDeg, 3.2f);
    }

    private void setTargetPoseInternal(String poseName, float durationSec, float speechSec, float lookYawDeg, float lookPitchDeg, float idleDelay) {
        Integer idx = poseNameMap.get(poseName);
        if (idx == null) idx = poseNameMap.getOrDefault("IDLE", 0);
        for (int b = 0; b < numBones; b++) {
            System.arraycopy(qCurrent[b], 0, qStart[b], 0, 4);
            System.arraycopy(poseQuats[idx][b], 0, qTarget[b], 0, 4);
        }
        animTime = 0.0f;
        animDuration = Math.max(0.12f, durationSec);
        speechTimeRemaining = speechSec;
        speechTotalDuration = Math.max(0.1f, speechSec);
        targetLookYaw = lookYawDeg;
        targetLookPitch = lookPitchDeg;
        hairVelX += (lookYawDeg >= 0 ? 1.8f : -1.8f);

        if (!"IDLE".equals(poseName)) {
            returnToIdleScheduled = true;
            returnToIdleCountdown = idleDelay;
        } else {
            returnToIdleScheduled = false;
        }
    }

    public synchronized void setEgoMode(boolean enabled) {
        this.egoEyesActive = enabled;
    }

    public synchronized float getEgoIntensity() {
        if (egoEyesActive) return 1.0f;
        if (egoPulseTime > 0.0f) return Math.min(1.0f, egoPulseTime * 0.8f);
        return 0.0f;
    }

    public synchronized void updateSkeleton(float dt) {
        if (dt > 0.1f) dt = 0.1f;
        totalTime += dt;

        if (egoPulseTime > 0.0f) {
            egoPulseTime = Math.max(0.0f, egoPulseTime - dt);
        }

        // Handle "6-7" alternating juggling dance
        if (sixSevenDanceRemaining > 0.0f) {
            sixSevenDanceRemaining -= dt;
            sixSevenStepTimer += dt;
            if (sixSevenStepTimer >= 0.25f) {
                sixSevenStepTimer = 0.0f;
                sixSevenToggle = !sixSevenToggle;
                String nextPose = sixSevenToggle ? "EASTER_67_B" : "EASTER_67_A";
                float yaw = sixSevenToggle ? 6.0f : -6.0f;
                setTargetPoseInternal(nextPose, 0.22f, speechTimeRemaining, yaw, 3.0f, 2.0f);
            }
        }

        if (returnToIdleScheduled && sixSevenDanceRemaining <= 0.0f) {
            returnToIdleCountdown -= dt;
            if (returnToIdleCountdown <= 0.0f) {
                returnToIdleScheduled = false;
                setTargetPoseInternal("IDLE", 0.55f, 0.0f, 0.0f, 0.0f, 0.0f);
            }
        }

        float t = 1.0f;
        if (animTime < animDuration) {
            animTime += dt;
            float u = Math.min(1.0f, animTime / animDuration);
            float inv = 1.0f - u;
            t = 1.0f - inv * inv * inv;
            t += 0.06f * (float) Math.sin(u * Math.PI) * u * (1.0f - u);
        }

        headLookYaw += (targetLookYaw - headLookYaw) * Math.min(1.0f, dt * 8.0f);
        headLookPitch += (targetLookPitch - headLookPitch) * Math.min(1.0f, dt * 8.0f);
        targetLookYaw *= (1.0f - Math.min(1.0f, dt * 1.5f));
        targetLookPitch *= (1.0f - Math.min(1.0f, dt * 1.5f));

        float headYawDelta = headLookYaw - prevHeadYaw;
        prevHeadYaw = headLookYaw;
        hairVelX += -headYawDelta * 0.8f - hairDispX * 28.0f * dt;
        hairVelX *= (float) Math.pow(0.08, dt);
        hairDispX += hairVelX * dt;

        float breath = (float) Math.sin(totalTime * 2.3f);
        float sway = (float) Math.sin(totalTime * 1.15f);

        float jawAngleDeg = 0.0f;
        if (speechTimeRemaining > 0.0f) {
            speechTimeRemaining = Math.max(0.0f, speechTimeRemaining - dt);
            float phase = (speechTotalDuration - speechTimeRemaining) * 20.0f;
            float env = Math.min(1.0f, speechTimeRemaining * 8.0f);
            jawAngleDeg = (4.0f + 8.5f * Math.abs((float) Math.sin(phase))) * env;
        }

        // Natural eyelid blinking every 2.8..4.5s + happy squint during 6-7 dance
        float eyelidAngleDeg = 0.0f;
        if (blinkProgress >= 0.0f) {
            blinkProgress += dt;
            if (blinkProgress >= BLINK_DURATION) {
                blinkProgress = -1.0f;
                blinkTimer = 2.7f + 1.6f * (float) Math.abs(Math.sin(totalTime * 7.13f));
            } else {
                float u = blinkProgress / BLINK_DURATION;
                eyelidAngleDeg = 20.5f * (float) Math.sin(u * Math.PI);
            }
        } else {
            blinkTimer -= dt;
            if (blinkTimer <= 0.0f) {
                blinkProgress = 0.0f;
            }
        }
        if (sixSevenDanceRemaining > 0.0f) {
            eyelidAngleDeg = Math.max(eyelidAngleDeg, 5.5f);
        }

        float eyeYawDeg = headLookYaw * 0.22f + (float) Math.sin(totalTime * 0.9f) * 0.8f;

        for (int b = 0; b < numBones; b++) {
            slerp(qStart[b], qTarget[b], t, qCurrent[b]);
            System.arraycopy(qCurrent[b], 0, tmpQuat, 0, 4);

            if (b == idxSpine1) {
                quatFromEulerXYZ(0.0f, sway * 0.6f, breath * 0.8f, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            } else if (b == idxChest) {
                quatFromEulerXYZ(sway * 1.1f, 0.0f, -breath * 1.4f, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            } else if (b == idxNeck) {
                quatFromEulerXYZ(headLookYaw * 0.35f, 0.0f, headLookPitch * 0.35f, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            } else if (b == idxHead) {
                quatFromEulerXYZ(sway * 0.8f, -headLookYaw * 0.65f, headLookPitch * 0.65f + breath * 0.7f, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            } else if (b == idxJaw && jawAngleDeg > 0.05f) {
                quatFromEulerXYZ(0.0f, 0.0f, jawAngleDeg, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            } else if (b == idxEyelid && eyelidAngleDeg > 0.05f) {
                quatFromEulerXYZ(0.0f, 0.0f, eyelidAngleDeg, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            } else if (b == idxEyeL || b == idxEyeR) {
                quatFromEulerXYZ(0.0f, -eyeYawDeg, -eyelidAngleDeg * 0.12f, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            } else if (b == idxHairF || b == idxHairT || b == idxHairB) {
                float hWave = (float) Math.sin(totalTime * 3.1f + b) * 1.5f + hairDispX * 12.0f;
                quatFromEulerXYZ(breath * 1.2f, hWave, hWave * 0.5f, procQuat);
                quatMul(tmpQuat, procQuat, tmpQuat);
            }

            quatToMat4(tmpQuat, tmpRotMat);
            int p = parentIdx[b];
            if (p < 0) {
                Matrix.multiplyMM(worldPose[b], 0, l0Matrices[b], 0, tmpRotMat, 0);
            } else {
                Matrix.multiplyMM(tmpMat, 0, worldPose[p], 0, l0Matrices[b], 0);
                Matrix.multiplyMM(worldPose[b], 0, tmpMat, 0, tmpRotMat, 0);
            }
            Matrix.multiplyMM(skinMatrices, b * 16, worldPose[b], 0, ibmMatrices[b], 0);
        }
    }

    private static void slerp(float[] a, float[] b, float t, float[] out) {
        float bx = b[0], by = b[1], bz = b[2], bw = b[3];
        float dot = a[0] * bx + a[1] * by + a[2] * bz + a[3] * bw;
        if (dot < 0.0f) {
            dot = -dot;
            bx = -bx; by = -by; bz = -bz; bw = -bw;
        }
        float s0, s1;
        if (1.0f - dot > 1e-4f) {
            double omega = Math.acos(Math.min(1.0f, dot));
            double invSin = 1.0 / Math.sin(omega);
            s0 = (float) (Math.sin((1.0 - t) * omega) * invSin);
            s1 = (float) (Math.sin(t * omega) * invSin);
        } else {
            s0 = 1.0f - t;
            s1 = t;
        }
        float rx = s0 * a[0] + s1 * bx;
        float ry = s0 * a[1] + s1 * by;
        float rz = s0 * a[2] + s1 * bz;
        float rw = s0 * a[3] + s1 * bw;
        float norm = (float) (1.0 / Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw));
        out[0] = rx * norm;
        out[1] = ry * norm;
        out[2] = rz * norm;
        out[3] = rw * norm;
    }

    private static void quatFromEulerXYZ(float rxDeg, float ryDeg, float rzDeg, float[] out) {
        double hx = Math.toRadians(rxDeg) * 0.5;
        double hy = Math.toRadians(ryDeg) * 0.5;
        double hz = Math.toRadians(rzDeg) * 0.5;
        float cx = (float) Math.cos(hx), sx = (float) Math.sin(hx);
        float cy = (float) Math.cos(hy), sy = (float) Math.sin(hy);
        float cz = (float) Math.cos(hz), sz = (float) Math.sin(hz);
        out[0] = sx * cy * cz + cx * sy * sz;
        out[1] = cx * sy * cz - sx * cy * sz;
        out[2] = cx * cy * sz + sx * sy * cz;
        out[3] = cx * cy * cz - sx * sy * sz;
    }

    private static void quatMul(float[] a, float[] b, float[] out) {
        float ax = a[0], ay = a[1], az = a[2], aw = a[3];
        float bx = b[0], by = b[1], bz = b[2], bw = b[3];
        out[0] = aw * bx + ax * bw + ay * bz - az * by;
        out[1] = aw * by - ax * bz + ay * bw + az * bx;
        out[2] = aw * bz + ax * by - ay * bx + az * bw;
        out[3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    private static void quatToMat4(float[] q, float[] m) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;

        m[0] = 1.0f - 2.0f * (yy + zz);
        m[1] = 2.0f * (xy + wz);
        m[2] = 2.0f * (xz - wy);
        m[3] = 0.0f;

        m[4] = 2.0f * (xy - wz);
        m[5] = 1.0f - 2.0f * (xx + zz);
        m[6] = 2.0f * (yz + wx);
        m[7] = 0.0f;

        m[8] = 2.0f * (xz + wy);
        m[9] = 2.0f * (yz - wx);
        m[10] = 1.0f - 2.0f * (xx + yy);
        m[11] = 0.0f;

        m[12] = 0.0f;
        m[13] = 0.0f;
        m[14] = 0.0f;
        m[15] = 1.0f;
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static float readFloatLE(DataInputStream in) throws IOException {
        return Float.intBitsToFloat(readIntLE(in));
    }

    private static String readStringLE(DataInputStream in) throws IOException {
        int len = in.readUnsignedByte() | (in.readUnsignedByte() << 8);
        byte[] buf = new byte[len];
        in.readFully(buf);
        return new String(buf, "UTF-8");
    }
}
