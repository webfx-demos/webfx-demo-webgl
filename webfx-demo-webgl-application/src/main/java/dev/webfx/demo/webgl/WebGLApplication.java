package dev.webfx.demo.webgl;

import dev.webfx.kit.util.scene.DeviceSceneUtil;
import dev.webfx.kit.webgl.*;
import dev.webfx.platform.console.Console;
import dev.webfx.platform.resource.Resource;
import dev.webfx.platform.uischeduler.UiScheduler;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.LinearGradient;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.joml.Matrix4d;

public class WebGLApplication extends Application {

    private Node webGLNode;

    @Override
    public void start(Stage primaryStage) {
        BorderPane borderPane = new BorderPane();
        borderPane.setBackground(Background.fill(LinearGradient.valueOf("to bottom, #B2F4B6, #3BF0E4, #C2A0FD, #EA5DAD, #FF7571, #FFE580")));
        Scene scene = DeviceSceneUtil.newScene(borderPane, 800, 600);
        primaryStage.setScene(scene);
        if (!WebGL.supportsWebGL()) {
            borderPane.setCenter(new Text("WebGL is not supported on this platform, sorry!"));
        } else {
            // Reading back the final scene width and height for the browser
            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();
            webGLNode = WebGL.createWebGLNode(sceneWidth, sceneHeight);
            borderPane.setCenter(webGLNode);
            UiScheduler.scheduleInAnimationFrame(this::runWebGL, 1);
        }
        primaryStage.show();
    }

    private void runWebGL() {
        WebGLRenderingContext gl = WebGL.getContext(webGLNode);
        // Set clear color to black, fully opaque
        gl.clearColor(0, 0, 1, 1);
        // Clear the color buffer with specified clear color
        gl.clear(gl.COLOR_BUFFER_BIT);

        // Vertex shader program
        String vsSource = "attribute vec4 aVertexPosition;\n" +
                          "  attribute vec2 aTextureCoord;\n" +
                          "\n" +
                          "  uniform mat4 uModelViewMatrix;\n" +
                          "  uniform mat4 uProjectionMatrix;\n" +
                          "\n" +
                          "  varying highp vec2 vTextureCoord;\n" +
                          "\n" +
                          "  void main(void) {\n" +
                          "    gl_Position = uProjectionMatrix * uModelViewMatrix * aVertexPosition;\n" +
                          "    vTextureCoord = aTextureCoord;\n" +
                          "  }";

        // Fragment shader program
        String fsSource = "varying highp vec2 vTextureCoord;\n" +
                          "\n" +
                          "  uniform sampler2D uSampler;\n" +
                          "\n" +
                          "  void main(void) {\n" +
                          "    gl_FragColor = texture2D(uSampler, vTextureCoord);\n" +
                          "  }";

        // Create the shader program
        WebGLProgram shaderProgram = initShaderProgram(gl, vsSource, fsSource);

        // Collect all the info needed to use the shader program.
        // Look up which attribute our shader program is using
        // for aVertexPosition and look up uniform locations.
        ProgramInfo programInfo = new ProgramInfo(
                shaderProgram,
                gl.getAttribLocation(shaderProgram, "aVertexPosition"),
                0, //gl.getAttribLocation(shaderProgram, "aVertexColor"),
                gl.getAttribLocation(shaderProgram, "aTextureCoord"),
                gl.getUniformLocation(shaderProgram, "uProjectionMatrix"),
                gl.getUniformLocation(shaderProgram, "uModelViewMatrix"),
                gl.getUniformLocation(shaderProgram, "uSampler")
        );

        // Here's where we call the routine that builds all the
        // objects we'll be drawing.
        Buffers buffers = initBuffers(gl);

        // Load texture
        WebGLTexture texture = loadTexture(gl, Resource.toUrl("WebFX-512x512.png", getClass()));
        // Flip image pixels into the bottom-to-top order that WebGL expects.
        gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);

        // Draw the scene
        new AnimationTimer() {
            double cubeRotation = 0.0;
            long then, deltaTime;

            @Override
            public void handle(long now) {
                deltaTime = now - then;
                then = now;
                drawScene(gl, programInfo, buffers, texture, cubeRotation);
                cubeRotation += deltaTime * 1d / 1_000_000_000; // convert to seconds
            }
        }.start();
    }

    private static class ProgramInfo {
        final WebGLProgram program;
        final int vertexPosition;
        final int vertexColor;
        final int textureCoord;
        final WebGLUniformLocation projectionMatrix;
        final WebGLUniformLocation modelViewMatrix;
        final WebGLUniformLocation uSampler;

        public ProgramInfo(WebGLProgram program, int vertexPosition, int vertexColor, int textureCoord, WebGLUniformLocation projectionMatrix, WebGLUniformLocation modelViewMatrix, WebGLUniformLocation uSampler) {
            this.program = program;
            this.vertexPosition = vertexPosition;
            this.vertexColor = vertexColor;
            this.textureCoord = textureCoord;
            this.projectionMatrix = projectionMatrix;
            this.modelViewMatrix = modelViewMatrix;
            this.uSampler = uSampler;
        }
    }

    private WebGLShader loadShader(WebGLRenderingContext gl, int type, String source) {
        WebGLShader shader = gl.createShader(type);

        // Send the source to the shader object
        gl.shaderSource(shader, source);

        // Compile the shader program
        gl.compileShader(shader);

        // See if it compiled successfully

        if (gl.getShaderParameter(shader, gl.COMPILE_STATUS) == null) {
            Console.log("An error occurred compiling the shaders:" + gl.getShaderInfoLog(shader));
            gl.deleteShader(shader);
            return null;
        }

        return shader;
    }

    // Initialize a shader program, so WebGL knows how to draw our data
    private WebGLProgram initShaderProgram(WebGLRenderingContext gl, String vsSource, String fsSource) {
        WebGLShader vertexShader = loadShader(gl, gl.VERTEX_SHADER, vsSource);
        WebGLShader fragmentShader = loadShader(gl, gl.FRAGMENT_SHADER, fsSource);

        // Create the shader program
        WebGLProgram shaderProgram = gl.createProgram();
        gl.attachShader(shaderProgram, vertexShader);
        gl.attachShader(shaderProgram, fragmentShader);
        gl.linkProgram(shaderProgram);

        // If creating the shader program failed, alert
        if (gl.getProgramParameter(shaderProgram, gl.LINK_STATUS) == null) {
            Console.log("Unable to initialize the shader program:" + gl.getProgramInfoLog(shaderProgram));
            return null;
        }

        return shaderProgram;
    }

    private Buffers initBuffers(WebGLRenderingContext gl) {
        WebGLBuffer positionBuffer = initPositionBuffer(gl);
        //WebGLBuffer colorBuffer = initColorBuffer(gl);
        WebGLBuffer textureCoordBuffer = initTextureBuffer(gl);
        WebGLBuffer indexBuffer = initIndexBuffer(gl);
        return new Buffers(positionBuffer, null, textureCoordBuffer, indexBuffer);
    }

    private static class Buffers {
        final WebGLBuffer position;
        final WebGLBuffer color;
        final WebGLBuffer textureCoord;
        final WebGLBuffer indices;

        public Buffers(WebGLBuffer position, WebGLBuffer color, WebGLBuffer textureCoord, WebGLBuffer indices) {
            this.position = position;
            this.color = color;
            this.textureCoord = textureCoord;
            this.indices = indices;
        }
    }

    private WebGLBuffer initPositionBuffer(WebGLRenderingContext gl) {
        // Create a buffer for the square's positions.
        WebGLBuffer positionBuffer = gl.createBuffer();

        // Select the positionBuffer as the one to apply buffer
        // operations to from here out.
        gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);

        // Now create an array of positions for the square.
        double[] positions = {
                // Front face
                -1.0, -1.0, 1.0, 1.0, -1.0, 1.0, 1.0, 1.0, 1.0, -1.0, 1.0, 1.0,

                // Back face
                -1.0, -1.0, -1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, 1.0, -1.0, -1.0,

                // Top face
                -1.0, 1.0, -1.0, -1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, -1.0,

                // Bottom face
                -1.0, -1.0, -1.0, 1.0, -1.0, -1.0, 1.0, -1.0, 1.0, -1.0, -1.0, 1.0,

                // Right face
                1.0, -1.0, -1.0, 1.0, 1.0, -1.0, 1.0, 1.0, 1.0, 1.0, -1.0, 1.0,

                // Left face
                -1.0, -1.0, -1.0, -1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, 1.0, -1.0,
        };

        // Now pass the list of positions into WebGL to build the
        // shape. We do this by creating a Float32Array from the
        // JavaScript array, then use it to fill the current buffer.
        gl.bufferData(gl.ARRAY_BUFFER, WebGL.createFloat32Array(positions), gl.STATIC_DRAW);

        return positionBuffer;
    }

    private WebGLBuffer initIndexBuffer(WebGLRenderingContext gl) {
        WebGLBuffer indexBuffer = gl.createBuffer();
        gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);

        // This array defines each face as two triangles, using the
        // indices into the vertex array to specify each triangle's
        // position.

        double[] indices = {
                0,
                1,
                2,
                0,
                2,
                3, // front
                4,
                5,
                6,
                4,
                6,
                7, // back
                8,
                9,
                10,
                8,
                10,
                11, // top
                12,
                13,
                14,
                12,
                14,
                15, // bottom
                16,
                17,
                18,
                16,
                18,
                19, // right
                20,
                21,
                22,
                20,
                22,
                23 // left
        };

        // Now send the element array to GL

        gl.bufferData(
                gl.ELEMENT_ARRAY_BUFFER,
                WebGL.createUint16Array(indices),
                gl.STATIC_DRAW
                );

        return indexBuffer;
    }

    private WebGLBuffer initTextureBuffer(WebGLRenderingContext gl) {
        WebGLBuffer textureCoordBuffer = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, textureCoordBuffer);

        double[] textureCoordinates = {
                // Front
                0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0,
                // Back
                0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0,
                // Top
                0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0,
                // Bottom
                0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0,
                // Right
                0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0,
                // Left
                0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0
        };

        gl.bufferData(
                gl.ARRAY_BUFFER,
                WebGL.createFloat32Array(textureCoordinates),
                gl.STATIC_DRAW
                );

        return textureCoordBuffer;
    }

    // Tell WebGL how to pull out the positions from the position
    // buffer into the vertexPosition attribute.
    private void setPositionAttribute(WebGLRenderingContext gl, Buffers buffers, ProgramInfo programInfo) {
        int numComponents = 3; // pull out 2 values per iteration
        int type = gl.FLOAT; // the data in the buffer is 32bit floats
        boolean normalize = false; // don't normalize
        int stride = 0; // how many bytes to get from one set of values to the next
        // 0 = use type and numComponents above
        int offset = 0; // how many bytes inside the buffer to start from
        gl.bindBuffer(gl.ARRAY_BUFFER, buffers.position);
        gl.vertexAttribPointer(
                programInfo.vertexPosition,
                numComponents,
                type,
                normalize,
                stride,
                offset
                );
        gl.enableVertexAttribArray(programInfo.vertexPosition);
    }

    //
    // Initialize a texture and load an image.
    // When the image finished loading copy it into the texture.
    //
    private WebGLTexture loadTexture(WebGLRenderingContext gl, String url) {
        WebGLTexture texture = gl.createTexture();
        gl.bindTexture(gl.TEXTURE_2D, texture);

        // Because images have to be downloaded over the internet
        // they might take a moment until they are ready.
        // Until then put a single pixel in the texture so we can
        // use it immediately. When the image has finished downloading
        // we'll update the texture with the contents of the image.
        int level = 0;
        int internalFormat = gl.RGBA;
        int width = 1;
        int height = 1;
        int border = 0;
        int srcFormat = gl.RGBA;
        int srcType = gl.UNSIGNED_BYTE;
        ArrayBuffer pixel = WebGL.createUint8Array(0, 0, 255, 255); // opaque blue
        gl.texImage2D(
                gl.TEXTURE_2D,
                level,
                internalFormat,
                width,
                height,
                border,
                srcFormat,
                srcType,
                pixel
                );

        Image image = new Image(url, true);
        image.progressProperty().addListener(observable -> {
            if (image.getProgress() >= 1) {
                gl.bindTexture(gl.TEXTURE_2D, texture);
                gl.texImage2D(
                        gl.TEXTURE_2D,
                        level,
                        internalFormat,
                        srcFormat,
                        srcType,
                        image
                        );

                // WebGL1 has different requirements for power of 2 images
                // vs. non power of 2 images so check if the image is a
                // power of 2 in both dimensions.
                if (isPowerOf2((int) image.getWidth()) && isPowerOf2((int) image.getHeight())) {
                    // Yes, it's a power of 2. Generate mips.
                    gl.generateMipmap(gl.TEXTURE_2D);
                } else {
                    // No, it's not a power of 2. Turn off mips and set
                    // wrapping to clamp to edge
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
                }
            }
        });

        return texture;
    }

    private boolean isPowerOf2(int value) {
        return (value & (value - 1)) == 0;
    }

    // tell webgl how to pull out the texture coordinates from buffer
    private void setTextureAttribute(WebGLRenderingContext gl, Buffers buffers, ProgramInfo programInfo) {
        int num = 2; // every coordinate composed of 2 values
        int type = gl.FLOAT; // the data in the buffer is 32-bit float
        boolean normalize = false; // don't normalize
        int stride = 0; // how many bytes to get from one set to the next
        int offset = 0; // how many bytes inside the buffer to start from
        gl.bindBuffer(gl.ARRAY_BUFFER, buffers.textureCoord);
        gl.vertexAttribPointer(
                programInfo.textureCoord,
                num,
                type,
                normalize,
                stride,
                offset
                );
        gl.enableVertexAttribArray(programInfo.textureCoord);
    }

    private void drawScene(WebGLRenderingContext gl, ProgramInfo programInfo, Buffers buffers, WebGLTexture texture, double cubeRotation) {
        gl.clearColor(0.0, 0.0, 0.0, 1.0); // Clear to black, fully opaque
        gl.clearDepth(1.0); // Clear everything
        gl.enable(gl.DEPTH_TEST); // Enable depth testing
        gl.depthFunc(gl.LEQUAL); // Near things obscure far things

        // Clear the canvas before we start drawing on it.
        gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

        // Create a perspective matrix, a special matrix that is
        // used to simulate the distortion of perspective in a camera.
        // Our field of view is 45 degrees, with a width/height
        // ratio that matches the display size of the canvas
        // and we only want to see objects between 0.1 units
        // and 100 units away from the camera.

        double fieldOfView = (45 * Math.PI) / 180; // in radians
        double aspect = WebGL.getWebGLNodeWidth(webGLNode) / WebGL.getWebGLNodeHeight(webGLNode);
        double zNear = 0.1;
        double zFar = 100.0;

        Matrix4d projectionMatrix4 = new Matrix4d();

        // note: glmatrix.js always has the first argument
        // as the destination to receive the result.
        projectionMatrix4.perspective(fieldOfView, aspect, zNear, zFar);

        // Set the drawing position to the "identity" point, which is
        // the center of the scene.
        Matrix4d modelViewMatrix4 = new Matrix4d();

        // Now move the drawing position a bit to where we want to
        // start drawing the square.
        modelViewMatrix4.translate(0, 0, -6); // amount to translate
        modelViewMatrix4.rotate(
                cubeRotation, 0, 0, 1) // // axis to rotate around (Z)
                .rotate(cubeRotation * 0.7, 0, 1, 0) // axis to rotate around (Y)
                .rotate(cubeRotation * 0.3, 1, 0, 0); // // axis to rotate around (X)

        // Tell WebGL how to pull out the positions from the position
        // buffer into the vertexPosition attribute.
        setPositionAttribute(gl, buffers, programInfo);
        //setColorAttribute(gl, buffers, programInfo);
        setTextureAttribute(gl, buffers, programInfo);

        // Tell WebGL which indices to use to index the vertices
        gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, buffers.indices);

        // Tell WebGL to use our program when drawing
        gl.useProgram(programInfo.program);

        // Set the shader uniforms
        gl.uniformMatrix4fv(
                programInfo.projectionMatrix,
                false,
                projectionMatrix4.get(new double[16])
                );
        gl.uniformMatrix4fv(
                programInfo.modelViewMatrix,
                false,
                modelViewMatrix4.get(new double[16])
                );

        // Tell WebGL we want to affect texture unit 0
        gl.activeTexture(gl.TEXTURE0);

        // Bind the texture to texture unit 0
        gl.bindTexture(gl.TEXTURE_2D, texture);

        // Tell the shader we bound the texture to texture unit 0
        gl.uniform1i(programInfo.uSampler, 0);

        int vertexCount = 36;
        int type = gl.UNSIGNED_SHORT;
        int offset = 0;
        gl.drawElements(gl.TRIANGLES, vertexCount, type, offset);
    }
}