package cxa.lineswallpaper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.badlogic.gdx.backends.android.AndroidGL20;
import com.badlogic.gdx.graphics.GL20;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
//import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

public class GLES20LinesRenderer implements GLSurfaceView.Renderer {
	
	AndroidGL20 GLES20 = new AndroidGL20();

	private static final int FLOAT_SIZE_BYTES = 4;

	private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;

	private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;

	private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

	private final float[] triangle_vertices_data_ = {
			// X, Y, Z, U, V
			1.0f, 0.0f, 0, 1.0f, 0.0f, 0.0f, 0.0f, 0, 0.0f, 0.0f, 1.0f, 1.0f,
			0, 1.0f, 1.0f, 0.0f, 1.0f, 0, 0.0f, 1.0f };

	private static final int LINE_COUNT = 1500;

	private FloatBuffer triangle_vertices_;

	private FloatBuffer line_vertices_;

	private final String vertex_shader_ = "uniform mat4 uMVPMatrix;\n"
			+ "attribute vec4 aPosition;\n" + "attribute vec2 aTextureCoord;\n"
			+ "varying vec2 vTextureCoord;\n" + "void main() {\n"
			+ "  gl_Position = uMVPMatrix * aPosition;\n"
			+ "  vTextureCoord = aTextureCoord;\n" + "}\n";

	private final String fragment_shader_ = "precision mediump float;\n"
			+ "varying vec2 vTextureCoord;\n"
			+ "uniform sampler2D sTexture;\n"
			+ "void main() {\n"
			+ "  gl_FragColor = texture2D(sTexture, vTextureCoord) * vec4(1,1,1,0.86);\n"
			+ "}\n";

	private final String line_vertex_shader_ = "uniform mat4 uMVPMatrix;\n"
			+ "uniform float delta;\n"
			+ "attribute vec4 aPosition;\n"
			+ "varying float vColor;\n"
			+ "void main() {\n"
			+ "  float z = aPosition.z + delta;\n"
			+ "  if(z > 1.0)\n"
			+ "    z = z - 1.0;\n"
			+ "  vColor = 0.15 * z;\n"
			+ "  gl_Position = uMVPMatrix * vec4(aPosition.x, aPosition.y, z, aPosition.w);\n"
			+ "}\n";

	private final String line_fragment_shader_ = "precision mediump float;\n"
			+ "uniform vec3 mColor;\n"
			+ "varying float vColor;\n"			
			+ "void main() {\n"
			+ "  gl_FragColor = vec4(vColor,vColor,vColor,1) * vec4(mColor.x,mColor.y,mColor.z,1);\n" + "}\n";

	private float[] MVP_matrix_ = new float[16];

	private float[] proj_matrix_ = new float[16];

	private float[] M_matrix_ = new float[16];

	private float[] V_matrix_ = new float[16];

	private float[] quad_matrix_ = new float[16];
	private float delta = 0.0f;
	private int program_;
	private int line_program_;
	private int MVP_matrix_handle_;

	private int position_handle_;
	private int texture_handle_;
	private int texture_loc_;
	private int line_MVP_matrix_handle_;

	private int line_position_handle_;

	private int line_delta_handle_;
	private int line_mColor_handle_;

	private int target_texture_;

	private int framebuffer_;

	private int framebuffer_width_ = 256;
	private int framebuffer_height_ = 256;
	private int surface_width_ = 256;
	private int surface_height_ = 256;
	
	private SharedPreferences preferences_;
	private SettingsUpdater settingsUpdater_;
	private float backgroundColorRed_ = 0.0f;
	private float backgroundColorGreen_ = 0.0f;
	private float backgroundColorBlue_ = 0.0f;
	private float linesColorRed_ = 1.0f;
	private float linesColorGreen_ = 1.0f;
	private float linesColorBlue_ = 1.0f;
	
	private static String TAG = "GLES20LinesRenderer";

	public GLES20LinesRenderer(Context context) {
		triangle_vertices_ = ByteBuffer
				.allocateDirect(
						triangle_vertices_data_.length * FLOAT_SIZE_BYTES)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		triangle_vertices_.put(triangle_vertices_data_).position(0);

		Random rnd = new Random();

		float[] line_data = new float[LINE_COUNT * 3];
		for (int i = 0; i < LINE_COUNT; ++i) {
			line_data[i * 3 + 0] = rnd.nextFloat() * 2 - 1;
			line_data[i * 3 + 1] = rnd.nextFloat() * 2 - 1;
			line_data[i * 3 + 2] = rnd.nextFloat();
		}

		line_vertices_ = ByteBuffer
				.allocateDirect(line_data.length * FLOAT_SIZE_BYTES)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		line_vertices_.put(line_data).position(0);
		
	}
	
	public void setSharedPreferences(SharedPreferences preferences)
	{
		settingsUpdater_ = new SettingsUpdater(this);
		preferences_ = preferences;
		preferences_.registerOnSharedPreferenceChangeListener(settingsUpdater_);
		settingsUpdater_.onSharedPreferenceChanged(preferences_, null);
	}
	
	private class SettingsUpdater implements SharedPreferences.OnSharedPreferenceChangeListener {
		private GLES20LinesRenderer renderer_;
		
		public SettingsUpdater(GLES20LinesRenderer renderer)
		{
			renderer_ = renderer;
		}
		
		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			try
			{
				int backgroundInt = sharedPreferences.getInt("backgroundColor", 0);
				int linesInt = sharedPreferences.getInt("linesColor", -1);
				Log.i(TAG, "PREF back = " + backgroundInt + " lines = " + linesInt);
				renderer_.setColors(backgroundInt, linesInt);
				
			}
			catch(final Exception e)
			{
				Log.e(TAG, "PREF init error: " + e);			
			}
		}
	}
	
	public void setColors(int backgroundInt, int linesInt)
	{	
		float scale = 1.0f / 255.0f;
		float scaleBackground = scale * 0.05f;
		backgroundColorRed_ = scaleBackground * Color.red(backgroundInt);
		backgroundColorGreen_ = scaleBackground * Color.green(backgroundInt);
		backgroundColorBlue_ = scaleBackground * Color.blue(backgroundInt);
		linesColorRed_ = scale * Color.red(linesInt);
		linesColorGreen_ = scale * Color.green(linesInt);
		linesColorBlue_ = scale * Color.blue(linesInt);	
	}

	private void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GL20.GL_NO_ERROR) {
			Log.e(TAG, op + ": glError " + error);
			throw new RuntimeException(op + ": glError " + error);
		}
	}
	private int createFrameBuffer(GL10 gl, int width, int height,
			int targetTextureId) {
		int framebuffer;
		ByteBuffer tmp = ByteBuffer.allocateDirect(4);
		tmp.order(ByteOrder.nativeOrder());
		IntBuffer fb = tmp.asIntBuffer();
		GLES20.glGenFramebuffers(1, fb);
		framebuffer = fb.get(0);
		GLES20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);

		GLES20.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER,
				GL20.GL_COLOR_ATTACHMENT0, GL10.GL_TEXTURE_2D,
				targetTextureId, 0);
		int status = GLES20.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER);
		if (status != GL20.GL_FRAMEBUFFER_COMPLETE) {
			throw new RuntimeException("Framebuffer is not complete: "
					+ Integer.toHexString(status));
		}
		GLES20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
		return framebuffer;
	}
	private int createProgram(String vertexSource, String fragmentSource) {
		int vertexShader = loadShader(GL20.GL_VERTEX_SHADER, vertexSource);
		if (vertexShader == 0) {
			return 0;
		}

		int pixelShader = loadShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
		if (pixelShader == 0) {
			return 0;
		}

		int program = GLES20.glCreateProgram();
		if (program != 0) {
			GLES20.glAttachShader(program, vertexShader);
			checkGlError("glAttachShader");
			GLES20.glAttachShader(program, pixelShader);
			checkGlError("glAttachShader");
			GLES20.glLinkProgram(program);
			//int[] linkStatus = new int[1];
			ByteBuffer tmp = ByteBuffer.allocateDirect(4);
			tmp.order(ByteOrder.nativeOrder());
			IntBuffer linkStatus = tmp.asIntBuffer();
			GLES20.glGetProgramiv(program, GL20.GL_LINK_STATUS, linkStatus);
			if (linkStatus.get(0) != GL20.GL_TRUE) {
				Log.e(TAG, "Could not link program: ");
				Log.e(TAG, GLES20.glGetProgramInfoLog(program));
				GLES20.glDeleteProgram(program);
				program = 0;
			}
		}
		return program;
	}
	private int createTargetTexture(GL10 gl, int width, int height) {
		int texture;
		//int[] textures = new int[1];
		ByteBuffer tmp = ByteBuffer.allocateDirect(4);
		tmp.order(ByteOrder.nativeOrder());
		IntBuffer textures = tmp.asIntBuffer();
		GLES20.glGenTextures(1, textures);
		texture = textures.get(0);
		updateTargetTexture(gl, texture, width, height);
		return texture;
	}
	float getTimeDeltaByScale(long scale) {
		if (scale < 1)
			return 0.0f;
		long time = SystemClock.uptimeMillis() % scale;
		return (float) ((int) time) / (float) scale;
	}
	private int loadShader(int shaderType, String source) {
		int shader = GLES20.glCreateShader(shaderType);
		Log.w(TAG, "loadShader returned " + Integer.valueOf(shader));
		if (shader != 0) {
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			ByteBuffer tmp = ByteBuffer.allocateDirect(4);
			tmp.order(ByteOrder.nativeOrder());
			IntBuffer compiled = tmp.asIntBuffer();
			Log.w(TAG, "Before glGetShaderiv");
			GLES20.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, compiled);
			Log.w(TAG, "glGetShaderiv ret=" + compiled.get(0));
			if (compiled.get(0) == 0) {
				Log.e(TAG, "Could not compile shader " + shaderType + ":");
				Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
				GLES20.glDeleteShader(shader);
				shader = 0;
			}
		}
		return shader;
	}
	@Override
	public void onDrawFrame(GL10 gl) {
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.
		GLES20.glClearColor(backgroundColorRed_, backgroundColorGreen_, backgroundColorBlue_, 1.0f);

		GLES20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer_);
		GLES20.glViewport(0, 0, framebuffer_width_, framebuffer_height_);
		GLES20.glClear(GL20.GL_COLOR_BUFFER_BIT);
		
		renderBlurTexture();
		renderLines();
		
		GLES20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
		GLES20.glViewport(0, 0, surface_width_, surface_height_);

		GLES20.glClear(GL20.GL_COLOR_BUFFER_BIT);

		renderBlurTexture();
	}
	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		// re-read settings
		if(settingsUpdater_ != null && preferences_ != null) {
			settingsUpdater_.onSharedPreferenceChanged(preferences_, null);
		}
		
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.
		GLES20.glViewport(0, 0, width, height);
		float scale = 0.1f;
		float ratio = scale * width / height;
		Matrix.frustumM(proj_matrix_, 0, -ratio, ratio, -scale, scale, 0.1f,
				100.0f);

		surface_width_ = width;
		surface_height_ = height;

		// lets make framebuffer have power of 2 dimension
		// and it should be less then display size
		framebuffer_width_ = 1 << (int)(Math.log(width)/Math.log(2));
		if(framebuffer_width_ == surface_width_) framebuffer_width_ >>= 1;
		framebuffer_height_ = 1 << (int)(Math.log(height)/Math.log(2));
		if(framebuffer_height_ == surface_height_) framebuffer_height_ >>= 1;
		
		// http://code.google.com/p/android/issues/detail?id=14835
		// The size of the FBO should have  square size.		
		if(framebuffer_height_ > framebuffer_width_) {
			framebuffer_width_ = framebuffer_height_; 
		} else if(framebuffer_width_ > framebuffer_height_) {
			framebuffer_height_ = framebuffer_width_;			 
		}
		
		//Log.i("BL***","framebuffer_width_=" + framebuffer_width_+" framebuffer_height_="+framebuffer_height_);
		
		updateTargetTexture(gl, target_texture_, framebuffer_width_,
				framebuffer_height_);
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.
		setupQuadShader();
		setupLinesShader();
		setupFramebuffer(gl);

		setLookAtM( V_matrix_, 0, 0, 0, 1.0f, 0f, 0f, 0f, 0f, -1.0f, 0.0f);
		Matrix.orthoM(quad_matrix_, 0, 0, 1, 0, 1, -1, 1);
	}
	private void renderBlurTexture() {
		GLES20.glUseProgram(program_);
		checkGlError("glUseProgram");

		GLES20.glActiveTexture(GL20.GL_TEXTURE0);
		GLES20.glBindTexture(GL20.GL_TEXTURE_2D, target_texture_);
		GLES20.glUniform1i(texture_loc_,0);

		triangle_vertices_.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
		GLES20.glVertexAttribPointer(position_handle_, 3, GL20.GL_FLOAT,
				false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangle_vertices_);
		checkGlError("glVertexAttribPointer maPosition");
		triangle_vertices_.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
		GLES20.glEnableVertexAttribArray(position_handle_);
		checkGlError("glEnableVertexAttribArray position_handle_");
		GLES20.glVertexAttribPointer(texture_handle_, 2, GL20.GL_FLOAT,
				false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangle_vertices_);
		checkGlError("glVertexAttribPointer texture_handle_");
		GLES20.glEnableVertexAttribArray(texture_handle_);
		checkGlError("glEnableVertexAttribArray texture_handle_");

		FloatBuffer fb = ByteBuffer.allocateDirect(quad_matrix_.length * FLOAT_SIZE_BYTES)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();
		fb.put(quad_matrix_).position(0);
		GLES20.glUniformMatrix4fv(MVP_matrix_handle_, 1, false, fb);
		GLES20.glDrawArrays(GL20.GL_TRIANGLE_STRIP, 0, 4);
		checkGlError("glDrawArrays");
	}
	private void renderLines() {
		GLES20.glBindTexture(GL20.GL_TEXTURE_2D, 0);
		GLES20.glEnable(GL20.GL_BLEND);
		GLES20.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

		GLES20.glUseProgram(line_program_);
		checkGlError("glUseProgram");

		line_vertices_.position(0);
		GLES20.glVertexAttribPointer(line_position_handle_, 3, GL20.GL_FLOAT,
				false, FLOAT_SIZE_BYTES * 3, line_vertices_);
		checkGlError("glVertexAttribPointer maPosition");
		GLES20.glEnableVertexAttribArray(line_position_handle_);
		checkGlError("glEnableVertexAttribArray position_handle_");

		float angle2 = 360.0f * getTimeDeltaByScale(1 * 50000L);
		Matrix.setRotateM(M_matrix_, 0, angle2, 0, 0, 1.0f);
		Matrix.multiplyMM(MVP_matrix_, 0, V_matrix_, 0, M_matrix_, 0);
		Matrix.multiplyMM(MVP_matrix_, 0, proj_matrix_, 0, MVP_matrix_, 0);

		delta = getTimeDeltaByScale(1 * 25000L);

		GLES20.glUniform1f(line_delta_handle_, delta);
		GLES20.glUniform3f(line_mColor_handle_, linesColorRed_, linesColorGreen_, linesColorBlue_);
		FloatBuffer fb = ByteBuffer.allocateDirect(MVP_matrix_.length * FLOAT_SIZE_BYTES)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();
		fb.put(MVP_matrix_).position(0);
		GLES20.glUniformMatrix4fv(line_MVP_matrix_handle_, 1, false, fb);
		GLES20.glDrawArrays(GL20.GL_LINES, 0, LINE_COUNT);
		checkGlError("glDrawArrays lines");
	}
	private void setupFramebuffer(GL10 gl) {
		target_texture_ = createTargetTexture(gl, framebuffer_width_,
				framebuffer_height_);
		if (target_texture_ == 0) {
			Log.e(TAG, "Could not create render texture");
			throw new RuntimeException("Could not create render texture");
		}

		framebuffer_ = createFrameBuffer(gl, framebuffer_width_,
				framebuffer_height_, target_texture_);
		if (framebuffer_ == 0) {
			Log.e(TAG, "Could not create frame buffer");
			throw new RuntimeException("Could not create frame buffer");
		}
	}
	private void setupLinesShader() {
		line_program_ = createProgram(line_vertex_shader_,
				line_fragment_shader_);
		if (line_program_ == 0) {
			throw new RuntimeException(
				"Line shader compilation failed");

		}
		line_position_handle_ = GLES20.glGetAttribLocation(line_program_,
				"aPosition");
		checkGlError("glGetAttribLocation aPosition");
		if (line_position_handle_ == -1) {
			throw new RuntimeException(
					"Could not get attrib location for aPosition");
		}
		
		texture_loc_ = GLES20.glGetUniformLocation(program_, "sTexture");
		checkGlError("glGetAttribLocation sTexture");

		line_delta_handle_ = GLES20.glGetUniformLocation(line_program_, "delta");
		checkGlError("glGetAttribLocation delta");
		if (line_delta_handle_ == -1) {
			throw new RuntimeException(
					"Could not get attrib location for delta");
		}
		
		line_mColor_handle_ = GLES20.glGetUniformLocation(line_program_, "mColor");
		checkGlError("glGetAttribLocation mColor");
		if (line_mColor_handle_ == -1) {
			throw new RuntimeException(
					"Could not get attrib location for mColor");
		}
		

		line_MVP_matrix_handle_ = GLES20.glGetUniformLocation(line_program_,
				"uMVPMatrix");
		checkGlError("glGetUniformLocation uMVPMatrix");
		if (line_MVP_matrix_handle_ == -1) {
			throw new RuntimeException(
					"Could not get attrib location for uMVPMatrix");
		}
	}
	private void setupQuadShader() {
		program_ = createProgram(vertex_shader_, fragment_shader_);
		if (program_ == 0) {
			throw new RuntimeException(
				"Quad shader compilation failed");
		}
		position_handle_ = GLES20.glGetAttribLocation(program_, "aPosition");
		checkGlError("glGetAttribLocation aPosition");
		if (position_handle_ == -1) {
			throw new RuntimeException(
					"Could not get attrib location for aPosition");
		}
		texture_handle_ = GLES20.glGetAttribLocation(program_, "aTextureCoord");
		checkGlError("glGetAttribLocation aTextureCoord");
		if (texture_handle_ == -1) {
			throw new RuntimeException(
					"Could not get attrib location for aTextureCoord");
		}

		MVP_matrix_handle_ = GLES20
				.glGetUniformLocation(program_, "uMVPMatrix");
		checkGlError("glGetUniformLocation uMVPMatrix");
		if (MVP_matrix_handle_ == -1) {
			throw new RuntimeException(
					"Could not get attrib location for uMVPMatrix");
		}
	}

	private void updateTargetTexture(GL10 gl, int texture, int width, int height) {
		GLES20.glBindTexture(GL20.GL_TEXTURE_2D, texture);
		GLES20.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGB, width,
				height, 0, GL20.GL_RGB, GL20.GL_UNSIGNED_BYTE, null);
		GLES20.glTexParameterf(GL20.GL_TEXTURE_2D,
				GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR);
		GLES20.glTexParameterf(GL20.GL_TEXTURE_2D,
				GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
	}
	
	/**
     * Define a viewing transformation in terms of an eye point, a center of
     * view, and an up vector.
     *
     * @param rm returns the result
     * @param rmOffset index into rm where the result matrix starts
     * @param eyeX eye point X
     * @param eyeY eye point Y
     * @param eyeZ eye point Z
     * @param centerX center of view X
     * @param centerY center of view Y
     * @param centerZ center of view Z
     * @param upX up vector X
     * @param upY up vector Y
     * @param upZ up vector Z
     */
    public static void setLookAtM(float[] rm, int rmOffset,
            float eyeX, float eyeY, float eyeZ,
            float centerX, float centerY, float centerZ, float upX, float upY,
            float upZ) {

        // See the OpenGL GLUT documentation for gluLookAt for a description
        // of the algorithm. We implement it in a straightforward way:

        float fx = centerX - eyeX;
        float fy = centerY - eyeY;
        float fz = centerZ - eyeZ;

        // Normalize f
        float rlf = 1.0f / Matrix.length(fx, fy, fz);
        fx *= rlf;
        fy *= rlf;
        fz *= rlf;

        // compute s = f x up (x means "cross product")
        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;

        // and normalize s
        float rls = 1.0f / Matrix.length(sx, sy, sz);
        sx *= rls;
        sy *= rls;
        sz *= rls;

        // compute u = s x f
        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        rm[rmOffset + 0] = sx;
        rm[rmOffset + 1] = ux;
        rm[rmOffset + 2] = -fx;
        rm[rmOffset + 3] = 0.0f;

        rm[rmOffset + 4] = sy;
        rm[rmOffset + 5] = uy;
        rm[rmOffset + 6] = -fy;
        rm[rmOffset + 7] = 0.0f;

        rm[rmOffset + 8] = sz;
        rm[rmOffset + 9] = uz;
        rm[rmOffset + 10] = -fz;
        rm[rmOffset + 11] = 0.0f;

        rm[rmOffset + 12] = 0.0f;
        rm[rmOffset + 13] = 0.0f;
        rm[rmOffset + 14] = 0.0f;
        rm[rmOffset + 15] = 1.0f;

        Matrix.translateM(rm, rmOffset, -eyeX, -eyeY, -eyeZ);
    }
}
