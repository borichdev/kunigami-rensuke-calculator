package dev.borich.kunigamicalc;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class KunigamiRenderer implements GLSurfaceView.Renderer {
    private final Context context;
    public final KunigamiModel model = new KunigamiModel();

    private int bgProgram;
    private int bgPosLoc, bgTimeLoc, bgAspectLoc, bgEgoLoc;
    private FloatBuffer bgQuadBuf;

    private int charProgram;
    private int cMvpLoc, cBonesLoc, cTexColLoc, cTexSdwLoc, cFlagsLoc, cTimeLoc, cEgoLoc, cCamPosLoc;

    private int outlineProgram;
    private int oMvpLoc, oBonesLoc, oOutlineWidthLoc, oOutlineColorLoc;

    private final float[] projMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] vpMatrix   = new float[16];

    private long lastFrameTimeMs = 0L;
    private float elapsedSec = 0.0f;
    private float aspect = 0.56f;

    // Camera orbit / zoom controls (zoomed out so full upper/mid body and all wide hand gestures fit comfortably)
    private float camYawDeg = 0.0f;
    private float camPitchDeg = 2.0f;
    private float camDistance = 2.15f;
    private float camTargetZ = 1.24f;
    private boolean autoCenterCamera = true;
    private boolean showcaseMode = false;

    private static final String BG_VERT =
            "#version 300 es\n" +
            "in vec2 a_pos;\n" +
            "out vec2 v_uv;\n" +
            "void main() {\n" +
            "    v_uv = a_pos * 0.5 + 0.5;\n" +
            "    gl_Position = vec4(a_pos, 0.999, 1.0);\n" +
            "}\n";

    private static final String BG_FRAG =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "in vec2 v_uv;\n" +
            "uniform float u_time;\n" +
            "uniform float u_aspect;\n" +
            "uniform float u_ego;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "float hexDist(vec2 p) {\n" +
            "    p = abs(p);\n" +
            "    return max(dot(p, normalize(vec2(1.0, 1.73205))), p.x);\n" +
            "}\n" +
            "\n" +
            "vec4 hexCoords(vec2 uv) {\n" +
            "    vec2 r = vec2(1.0, 1.73205);\n" +
            "    vec2 h = r * 0.5;\n" +
            "    vec2 a = mod(uv, r) - h;\n" +
            "    vec2 b = mod(uv - h, r) - h;\n" +
            "    vec2 gv = dot(a, a) < dot(b, b) ? a : b;\n" +
            "    float x = atan(gv.x, gv.y);\n" +
            "    float y = 0.5 - hexDist(gv);\n" +
            "    vec2 id = uv - gv;\n" +
            "    return vec4(x, y, id.x, id.y);\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 p = (v_uv - vec2(0.5, 0.58)) * vec2(u_aspect, 1.0);\n" +
            "    float dist = length(p);\n" +
            "\n" +
            "    // Kunigami warm dark obsidian-amber base gradient\n" +
            "    vec3 colTop = mix(vec3(0.11, 0.06, 0.05), vec3(0.22, 0.05, 0.02), u_ego);\n" +
            "    vec3 colMid = mix(vec3(0.20, 0.09, 0.05), vec3(0.38, 0.11, 0.03), u_ego);\n" +
            "    vec3 colGlow = mix(vec3(0.95, 0.42, 0.10), vec3(1.0, 0.32, 0.04), u_ego);\n" +
            "    vec3 col = mix(colMid, colTop, smoothstep(0.0, 1.0, v_uv.y));\n" +
            "\n" +
            "    // Radial spotlight behind Kunigami's upper body\n" +
            "    float halo = exp(-dist * dist * (3.8 - 1.2 * u_ego));\n" +
            "    col += colGlow * halo * (0.34 + 0.32 * u_ego);\n" +
            "\n" +
            "    // Blue Lock tactical hexagonal grid\n" +
            "    vec2 huv = p * 7.5 + vec2(0.0, -u_time * 0.12);\n" +
            "    vec4 hc = hexCoords(huv);\n" +
            "    float hexLine = smoothstep(0.06, 0.015, hc.y);\n" +
            "    float pulse = 0.5 + 0.5 * sin(u_time * 2.2 + hc.z * 1.7 + hc.w * 2.3);\n" +
            "    col += colGlow * hexLine * (0.06 + 0.12 * pulse + 0.16 * u_ego) * exp(-dist * 1.8);\n" +
            "\n" +
            "    // Diagonal striker speed lines / jersey #50 accent bars\n" +
            "    float diag = abs( fract((p.x * 1.4 + p.y * 0.8) * 3.0 - u_time * 0.08) - 0.5 );\n" +
            "    float stripe = smoothstep(0.48, 0.495, diag);\n" +
            "    col += vec3(1.0, 0.50, 0.15) * stripe * (0.04 + 0.12 * u_ego);\n" +
            "\n" +
            "    // Rising fiery Ego embers\n" +
            "    for (int i = 0; i < 6; i++) {\n" +
            "        float fi = float(i);\n" +
            "        float speed = 0.22 + fi * 0.05 + u_ego * 0.25;\n" +
            "        float xOff = sin(fi * 4.13 + u_time * (0.7 + fi * 0.1)) * 0.24;\n" +
            "        float yPos = fract(u_time * speed + fi * 0.19) * 1.2 - 0.5;\n" +
            "        vec2 ep = p - vec2(xOff, yPos);\n" +
            "        float spark = exp(-dot(ep, ep) * (1400.0 - 500.0 * u_ego));\n" +
            "        col += vec3(1.0, 0.62, 0.20) * spark * (0.45 + 0.55 * u_ego);\n" +
            "    }\n" +
            "\n" +
            "    fragColor = vec4(col, 1.0);\n" +
            "}\n";

    private static final String CHAR_VERT =
            "#version 300 es\n" +
            "layout(location = 0) in vec3 a_pos;\n" +
            "layout(location = 1) in vec3 a_norm;\n" +
            "layout(location = 2) in vec2 a_uv;\n" +
            "layout(location = 3) in vec4 a_joints;\n" +
            "layout(location = 4) in vec4 a_weights;\n" +
            "\n" +
            "uniform mat4 u_mvp;\n" +
            "uniform mat4 u_bones[57];\n" +
            "\n" +
            "out vec3 v_worldPos;\n" +
            "out vec3 v_worldNorm;\n" +
            "out vec2 v_uv;\n" +
            "\n" +
            "void main() {\n" +
            "    ivec4 j = ivec4(a_joints + 0.5);\n" +
            "    mat4 skin = u_bones[j.x] * a_weights.x\n" +
            "              + u_bones[j.y] * a_weights.y\n" +
            "              + u_bones[j.z] * a_weights.z\n" +
            "              + u_bones[j.w] * a_weights.w;\n" +
            "    vec4 wp = skin * vec4(a_pos, 1.0);\n" +
            "    vec3 wn = normalize(mat3(skin) * a_norm);\n" +
            "    v_worldPos = wp.xyz;\n" +
            "    v_worldNorm = wn;\n" +
            "    v_uv = a_uv;\n" +
            "    gl_Position = u_mvp * wp;\n" +
            "}\n";

    private static final String CHAR_FRAG =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "in vec3 v_worldPos;\n" +
            "in vec3 v_worldNorm;\n" +
            "in vec2 v_uv;\n" +
            "\n" +
            "uniform sampler2D u_texCol;\n" +
            "uniform sampler2D u_texSdw;\n" +
            "uniform int u_flags;\n" +
            "uniform float u_time;\n" +
            "uniform float u_ego;\n" +
            "uniform vec3 u_camPos;\n" +
            "\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 colSample = texture(u_texCol, v_uv);\n" +
            "    vec4 sdwSample = texture(u_texSdw, v_uv);\n" +
            "\n" +
            "    // Ego spiral eyes special shader\n" +
            "    if ((u_flags & 4) != 0) {\n" +
            "        float lum = dot(colSample.rgb, vec3(0.333));\n" +
            "        if (lum < 0.08) discard;\n" +
            "        float pulse = 0.82 + 0.18 * sin(u_time * 9.0);\n" +
            "        vec3 egoColor = mix(vec3(1.0, 0.38, 0.04), vec3(1.0, 0.82, 0.30), lum) * 1.45 * pulse;\n" +
            "        fragColor = vec4(egoColor, clamp(lum * 1.25 * u_ego, 0.0, 1.0));\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    vec3 N = normalize(v_worldNorm);\n" +
            "    vec3 L = normalize(vec3(0.28, -0.88, 0.38));\n" +
            "    vec3 V = normalize(u_camPos - v_worldPos);\n" +
            "\n" +
            "    // Soft anime face & hair shading so face and bangs stay clean, bright and crisp\n" +
            "    bool isFace = (u_flags & 1) != 0;\n" +
            "    bool isHair = (u_flags & 2) != 0;\n" +
            "    if (isFace) {\n" +
            "        N = normalize(mix(N, vec3(0.0, -0.94, 0.34), 0.68));\n" +
            "    } else if (isHair) {\n" +
            "        N = normalize(mix(N, vec3(0.0, -0.88, 0.46), 0.58));\n" +
            "    }\n" +
            "\n" +
            "    float ndotl = dot(N, L);\n" +
            "    float cel = (isFace || isHair) ? smoothstep(-0.24, 0.06, ndotl) : smoothstep(-0.08, 0.06, ndotl);\n" +
            "\n" +
            "    vec3 baseCol = colSample.rgb;\n" +
            "    vec3 shadeCol = isHair ? mix(baseCol * 0.86, sdwSample.rgb, 0.42) : mix(baseCol * 0.78, sdwSample.rgb, 0.65);\n" +
            "\n" +
            "    // Boost Kunigami's iconic fiery orange hair saturation and crown-to-tips gradient\n" +
            "    if (isHair) {\n" +
            "        float crownGrad = clamp((v_worldPos.z - 1.66) / 0.24, 0.0, 1.0);\n" +
            "        vec3 hairTint = mix(vec3(1.10, 0.80, 0.58), vec3(1.15, 0.92, 0.72), crownGrad);\n" +
            "        baseCol *= hairTint;\n" +
            "        shadeCol *= vec3(1.08, 0.78, 0.58);\n" +
            "    }\n" +
            "\n" +
            "    vec3 lit = mix(shadeCol, baseCol, cel);\n" +
            "\n" +
            "    // Warm skin subsurface scattering (SSS) at cel terminator & subtle cheek vitality\n" +
            "    if (isFace) {\n" +
            "        float sss = cel * (1.0 - cel) * 4.0;\n" +
            "        lit += vec3(0.10, 0.032, 0.012) * sss;\n" +
            "        float cheekX = abs(v_worldPos.x) - 0.034;\n" +
            "        float cheekZ = v_worldPos.z - 1.696;\n" +
            "        float blush = exp(-(cheekX * cheekX * 4200.0 + cheekZ * cheekZ * 5500.0)) * step(v_worldPos.y, -0.088);\n" +
            "        lit = mix(lit, lit * vec3(1.04, 0.93, 0.90), blush * 0.65);\n" +
            "    }\n" +
            "\n" +
            "    // Anime hair specular angel-ring highlight\n" +
            "    if (isHair) {\n" +
            "        vec3 H = normalize(L + V + vec3(0.0, 0.0, 0.25));\n" +
            "        float ndoth = max(dot(N, H), 0.0);\n" +
            "        float spec = smoothstep(0.85, 0.92, ndoth) + 0.45 * smoothstep(0.95, 0.985, ndoth);\n" +
            "        lit += vec3(0.30, 0.16, 0.05) * spec;\n" +
            "    }\n" +
            "\n" +
            "    // Fiery rim light on silhouette edges\n" +
            "    float rim = pow(1.0 - max(dot(N, V), 0.0), 3.6);\n" +
            "    float rimStep = smoothstep(0.42, 0.65, rim) * (0.28 + 0.45 * u_ego);\n" +
            "    lit += vec3(1.0, 0.44, 0.12) * rimStep;\n" +
            "\n" +
            "    fragColor = vec4(lit, 1.0);\n" +
            "}\n";

    private static final String OUTLINE_VERT =
            "#version 300 es\n" +
            "layout(location = 0) in vec3 a_pos;\n" +
            "layout(location = 1) in vec3 a_norm;\n" +
            "layout(location = 3) in vec4 a_joints;\n" +
            "layout(location = 4) in vec4 a_weights;\n" +
            "\n" +
            "uniform mat4 u_mvp;\n" +
            "uniform mat4 u_bones[57];\n" +
            "uniform float u_outlineWidth;\n" +
            "\n" +
            "void main() {\n" +
            "    ivec4 j = ivec4(a_joints + 0.5);\n" +
            "    mat4 skin = u_bones[j.x] * a_weights.x\n" +
            "              + u_bones[j.y] * a_weights.y\n" +
            "              + u_bones[j.z] * a_weights.z\n" +
            "              + u_bones[j.w] * a_weights.w;\n" +
            "    vec4 wp = skin * vec4(a_pos, 1.0);\n" +
            "    vec3 wn = normalize(mat3(skin) * a_norm);\n" +
            "    wp.xyz += wn * u_outlineWidth;\n" +
            "    vec4 clipPos = u_mvp * wp;\n" +
            "    clipPos.z += 0.0022 * clipPos.w;\n" +
            "    gl_Position = clipPos;\n" +
            "}\n";

    private static final String OUTLINE_FRAG =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "uniform vec4 u_outlineColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = u_outlineColor;\n" +
            "}\n";

    public KunigamiRenderer(Context context) {
        this.context = context;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.10f, 0.05f, 0.04f, 1.0f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        bgProgram = createProgram(BG_VERT, BG_FRAG);
        bgPosLoc = GLES30.glGetAttribLocation(bgProgram, "a_pos");
        bgTimeLoc = GLES30.glGetUniformLocation(bgProgram, "u_time");
        bgAspectLoc = GLES30.glGetUniformLocation(bgProgram, "u_aspect");
        bgEgoLoc = GLES30.glGetUniformLocation(bgProgram, "u_ego");

        float[] quad = {
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
        };
        bgQuadBuf = ByteBuffer.allocateDirect(quad.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        bgQuadBuf.put(quad).position(0);

        charProgram = createProgram(CHAR_VERT, CHAR_FRAG);
        cMvpLoc = GLES30.glGetUniformLocation(charProgram, "u_mvp");
        cBonesLoc = GLES30.glGetUniformLocation(charProgram, "u_bones");
        cTexColLoc = GLES30.glGetUniformLocation(charProgram, "u_texCol");
        cTexSdwLoc = GLES30.glGetUniformLocation(charProgram, "u_texSdw");
        cFlagsLoc = GLES30.glGetUniformLocation(charProgram, "u_flags");
        cTimeLoc = GLES30.glGetUniformLocation(charProgram, "u_time");
        cEgoLoc = GLES30.glGetUniformLocation(charProgram, "u_ego");
        cCamPosLoc = GLES30.glGetUniformLocation(charProgram, "u_camPos");

        outlineProgram = createProgram(OUTLINE_VERT, OUTLINE_FRAG);
        oMvpLoc = GLES30.glGetUniformLocation(outlineProgram, "u_mvp");
        oBonesLoc = GLES30.glGetUniformLocation(outlineProgram, "u_bones");
        oOutlineWidthLoc = GLES30.glGetUniformLocation(outlineProgram, "u_outlineWidth");
        oOutlineColorLoc = GLES30.glGetUniformLocation(outlineProgram, "u_outlineColor");

        try {
            model.load(context);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load kunigami.k3d", e);
        }
        lastFrameTimeMs = SystemClock.uptimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        aspect = (float) width / Math.max(1, height);
        Matrix.perspectiveM(projMatrix, 0, 44.0f, aspect, 0.15f, 15.0f);
    }

    public synchronized void onTouchDrag(float dxPx, float dyPx, int viewWidth, int viewHeight) {
        camYawDeg += (dxPx / Math.max(1, viewWidth)) * 95.0f;
        camYawDeg = Math.max(-55.0f, Math.min(55.0f, camYawDeg));

        camPitchDeg += (dyPx / Math.max(1, viewHeight)) * 45.0f;
        camPitchDeg = Math.max(-15.0f, Math.min(22.0f, camPitchDeg));
    }

    public synchronized void setShowcaseMode(boolean showcase) {
        this.showcaseMode = showcase;
        this.autoCenterCamera = !showcase;
    }

    public synchronized void resetCamera() {
        camYawDeg = 0.0f;
        camPitchDeg = 2.0f;
        camDistance = 2.12f;
        camTargetZ = 1.16f;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = SystemClock.uptimeMillis();
        float dt = (now - lastFrameTimeMs) * 0.001f;
        lastFrameTimeMs = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;
        elapsedSec += dt;

        float targetZ;
        float targetDist;
        synchronized (this) {
            if (autoCenterCamera) {
                camYawDeg *= (float) Math.pow(0.08, dt);
                camPitchDeg += (2.0f - camPitchDeg) * Math.min(1.0f, dt * 4.0f);
            }
            float aspectScale = Math.max(1.0f, 0.50f / Math.max(0.35f, aspect));
            targetZ = showcaseMode ? 1.22f : 1.16f;
            targetDist = (showcaseMode ? 1.88f : 2.12f) * aspectScale;
            camTargetZ += (targetZ - camTargetZ) * Math.min(1.0f, dt * 6.0f);
            camDistance += (targetDist - camDistance) * Math.min(1.0f, dt * 6.0f);
        }

        model.updateSkeleton(dt);
        float ego = model.getEgoIntensity();

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        // 1. Draw dynamic background
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glUseProgram(bgProgram);
        GLES30.glUniform1f(bgTimeLoc, elapsedSec);
        GLES30.glUniform1f(bgAspectLoc, aspect);
        GLES30.glUniform1f(bgEgoLoc, ego);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glEnableVertexAttribArray(bgPosLoc);
        GLES30.glVertexAttribPointer(bgPosLoc, 2, GLES30.GL_FLOAT, false, 0, bgQuadBuf);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(bgPosLoc);

        // 2. Setup camera view matrix (character stands along +Z, faces -Y)
        double yawRad = Math.toRadians(camYawDeg);
        double pitchRad = Math.toRadians(camPitchDeg);
        float camX = (float) (Math.sin(yawRad) * Math.cos(pitchRad) * camDistance);
        float camY = (float) (-Math.cos(yawRad) * Math.cos(pitchRad) * camDistance);
        float camZ = camTargetZ + (float) (Math.sin(pitchRad) * camDistance * 0.55f);

        // Look target frames Kunigami from thighs/knees up to above his raised hands above the calculator keypad
        float lookAtZ = showcaseMode ? 1.24f : 1.12f;
        Matrix.setLookAtM(viewMatrix, 0,
                camX, camY, camZ,
                0.0f, -0.02f, lookAtZ,
                0.0f, 0.0f, 1.0f);
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        // 3. Pass 1: Inverted-hull anime outline pass
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_FRONT);
        GLES30.glUseProgram(outlineProgram);
        GLES30.glUniformMatrix4fv(oMvpLoc, 1, false, vpMatrix, 0);
        GLES30.glUniformMatrix4fv(oBonesLoc, model.numBones, false, model.skinMatrices, 0);
        GLES30.glUniform1f(oOutlineWidthLoc, 0.0019f + 0.0005f * ego);
        if (ego > 0.2f) {
            GLES30.glUniform4f(oOutlineColorLoc, 0.28f + 0.45f * ego, 0.11f + 0.15f * ego, 0.04f, 1.0f);
        } else {
            GLES30.glUniform4f(oOutlineColorLoc, 0.11f, 0.05f, 0.03f, 1.0f);
        }

        final int stride = 16 * 4;
        for (KunigamiModel.Submesh sm : model.submeshes) {
            if ((sm.flags & (KunigamiModel.FLAG_NO_OUTLINE | KunigamiModel.FLAG_EYE_EGO)) != 0) {
                continue;
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sm.vboId);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, sm.iboId);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4);
            GLES30.glEnableVertexAttribArray(3);
            GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 8 * 4);
            GLES30.glEnableVertexAttribArray(4);
            GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, stride, 12 * 4);

            GLES30.glDrawElements(GLES30.GL_TRIANGLES, sm.indexCount, GLES30.GL_UNSIGNED_SHORT, 0);
        }

        // 4. Pass 2: Main dual-texture anime cel-shaded character pass
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glUseProgram(charProgram);
        GLES30.glUniformMatrix4fv(cMvpLoc, 1, false, vpMatrix, 0);
        GLES30.glUniformMatrix4fv(cBonesLoc, model.numBones, false, model.skinMatrices, 0);
        GLES30.glUniform1f(cTimeLoc, elapsedSec);
        GLES30.glUniform1f(cEgoLoc, ego);
        GLES30.glUniform3f(cCamPosLoc, camX, camY, camZ);
        GLES30.glUniform1i(cTexColLoc, 0);
        GLES30.glUniform1i(cTexSdwLoc, 1);

        // Draw opaque submeshes first, then Ego eyes if active
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                if (ego <= 0.02f) break;
                GLES30.glEnable(GLES30.GL_BLEND);
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE);
                GLES30.glDepthMask(false);
            } else {
                GLES30.glDisable(GLES30.GL_BLEND);
                GLES30.glDepthMask(true);
            }

            for (KunigamiModel.Submesh sm : model.submeshes) {
                boolean isEgoMesh = (sm.flags & KunigamiModel.FLAG_EYE_EGO) != 0;
                if ((pass == 0 && isEgoMesh) || (pass == 1 && !isEgoMesh)) {
                    continue;
                }

                GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sm.colTexId);
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sm.sdwTexId);
                GLES30.glUniform1i(cFlagsLoc, sm.flags);

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sm.vboId);
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, sm.iboId);

                GLES30.glEnableVertexAttribArray(0);
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0);
                GLES30.glEnableVertexAttribArray(1);
                GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4);
                GLES30.glEnableVertexAttribArray(2);
                GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4);
                GLES30.glEnableVertexAttribArray(3);
                GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 8 * 4);
                GLES30.glEnableVertexAttribArray(4);
                GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, stride, 12 * 4);

                GLES30.glDrawElements(GLES30.GL_TRIANGLES, sm.indexCount, GLES30.GL_UNSIGNED_SHORT, 0);
            }
        }

        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static int createProgram(String vertSrc, String fragSrc) {
        int vs = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc);
        int fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc);
        int prog = GLES30.glCreateProgram();
        GLES30.glAttachShader(prog, vs);
        GLES30.glAttachShader(prog, fs);
        GLES30.glLinkProgram(prog);
        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(prog);
            GLES30.glDeleteProgram(prog);
            throw new RuntimeException("Program link error: " + log);
        }
        return prog;
    }

    private static int compileShader(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] status = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new RuntimeException("Shader compile error: " + log);
        }
        return s;
    }
}
