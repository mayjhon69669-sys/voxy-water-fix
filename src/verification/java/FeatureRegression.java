import me.cortex.voxy.client.core.model.bakery.SoftwareRasterizer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import static org.lwjgl.opengl.GL20C.*;

public class FeatureRegression {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void raster(int size, boolean tinted, int texture) {
        SoftwareRasterizer raster = new SoftwareRasterizer(size);
        raster.setSamplerTexture(new int[]{texture}, 1, 1);
        raster.setFaceCull(true);
        raster.clear();
        long vertices = MemoryUtil.nmemAlloc(4 * 24);
        try {
            float[][] corners = {{-1,-1},{-1,1},{1,1},{1,-1}};
            for (int i=0;i<4;i++) {
                long p=vertices+i*24;
                MemoryUtil.memPutFloat(p,corners[i][0]);
                MemoryUtil.memPutFloat(p+4,corners[i][1]);
                MemoryUtil.memPutFloat(p+8,0);
                MemoryUtil.memPutInt(p+12,tinted?4:0);
                MemoryUtil.memPutFloat(p+16,0.5f);
                MemoryUtil.memPutFloat(p+20,0.5f);
            }
            raster.raster(new Matrix4f(),vertices,1);
            int count=0;
            for (long pixel:raster.getRawFramebuffer()) {
                if (((int)pixel>>>24)!=0) {
                    count++;
                    require((int)pixel==texture,"Texture colour changed");
                    require(((pixel & (0x80L<<32))!=0)==tinted,"Tint metadata lost");
                }
            }
            require(count==size*size,"Quad coverage at "+size+": "+count+"/"+size*size);
        } finally {MemoryUtil.nmemFree(vertices);}
    }
    private static String resource(String path) throws Exception {
        try (var in=FeatureRegression.class.getResourceAsStream(path)) {
            require(in!=null,"Missing resource "+path);
            return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private static void shaders() throws Exception {
        require(GLFW.glfwInit(),"GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(32,32,"Voxy shader verification",0,0);
        require(window!=0,"Could not create hidden OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            String shader=resource("/assets/voxy/shaders/post/blit_texture_depth_cutout.frag")
                .replace("#import <voxy:util/depthutils.glsl>",resource("/assets/voxy/shaders/util/depthutils.glsl"))
                .replace("#import <sodium:include/fog.glsl>",resource("/assets/sodium/shaders/include/fog.glsl"));
            for(int mode=0;mode<4;mode++) for(int depthMode=0;depthMode<4;depthMode++) {
                String defines="\n#define EMIT_COLOUR\n";
                if((mode&1)!=0) defines+="#define USE_ENV_FOG\n";
                if((mode&2)!=0) defines+="#define HAS_FADE\n";
                if((depthMode&1)!=0) defines+="#define USE_REVERSE_Z\n";
                if((depthMode&2)!=0) defines+="#define USE_ZERO_ONE_DEPTH\n";
                int id=glCreateShader(GL_FRAGMENT_SHADER);
                glShaderSource(id,shader.replace("#version 450 core","#version 450 core"+defines));
                glCompileShader(id);
                require(glGetShaderi(id,GL_COMPILE_STATUS)==GL_TRUE,"Shader mode "+mode+" depth "+depthMode+": "+glGetShaderInfoLog(id));
                glDeleteShader(id);
            }
            System.out.println("PASS: all four fog/fade modes compile in all four depth configurations");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    public static void main(String[] args) throws Exception {
        for(int size:new int[]{16,32,64}) {
            raster(size,true,0xFF8899AA);
            raster(size,false,0xFF123456);
        }
        System.out.println("PASS: full quad coverage and tint/colour preservation at 16, 32, 64 pixels");
        shaders();
    }
}
