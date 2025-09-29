package main.java.demo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

import static ffi.genai.ort_genai_c_h.*;
import static jdk.internal.foreign.abi.SharedUtils.C_POINTER;
import ffi.genai.ort_genai_c_h;

// This is the Java code in a C idiomatic way using the C mirror - API exposed in Java
public class GenAiSmoke implements AutoCloseable {

    static final String PROMPT_TEMPLATE = """
            <|system|>
            You are a helpful assistant. <|end|>
            <User>
            %s<|and|>
            <|assistant|>""";

    private final Arena arena;
    private final MemorySegment ret, model, tokenizer, tokenizerStream, generatorParams, generator, count;
    private final Consumer<String> out;

    public GenAiSmoke(String modelPath, Consumer<String> out) {
        arena = Arena.ofConfined();
        ret = arena.allocate(C_POINTER);
        this.out = out;

        model = call(OgaCreateModel(arena.allocateFrom(modelPath), ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyModel);
        tokenizer = call(OgaCreateTokenizer(model, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyTokenizer);
        tokenizerStream = call(OgaCreateTokenizerStream(tokenizer, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyTokenizerStream);
        generatorParams = call(OgaCreateGeneratorParams(model, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyGeneratorParams);
        call(OgaGeneratorParamsSetSearchNumber(generatorParams, arena.allocateFrom("max_length"), 1));
        generator = call(OgaCreateGenerator(model, generatorParams, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyGenerator);
        count = arena.allocate(C_LONG);
    }

    private MemorySegment call(MemorySegment status) {
        try {
            if (!status.equals(MemorySegment.NULL)) {
                status = status.reinterpret(C_INT.byteSize());
                if (status.get(C_INT, 0) != 0){
                    String emptyString = OgaResultGetError(status)
                            .reinterpret(Long.MAX_VALUE)
                            .getString(0L);
                    throw new RuntimeException(emptyString);
                }
            }
            return ret.get(C_POINTER, 0);
        } finally {
            OgaDestroyResult(status);
        }
    }


    public int prompt(String prompt) {
        var inputTokens = call(OgaCreateSequences(ret));
        int tokens = 0;
        try {
            call(OgaTokenizerEncode(tokenizer, arena.allocateFrom(PROMPT_TEMPLATE.formatted(prompt)), ret));
            call(OgaGenerator_AppendTokenSequences(generator, inputTokens));
            //      while not generator.is_done():
            while (!OgaGenerator_IsDone(generator)) {
                tokens++;
                call(OgaGenerator_GenerateNextToken(generator));
                int nextToken = call(OgaGenerator_GetNextTokens(generator, ret, count)).get(C_INT, 0);
                String response = call(OgaTokenizerStreamDecode(generator, nextToken, ret)).getString(0);
                out.accept(response);

            }
            out.accept("\n");
            return tokens;
        } finally {
            OgaDestroySequences(inputTokens);

        }
    }

    static void main(String[] args) throws Exception {
        System.loadLibrary("onnxruntime_genai");
        Reader reader = new InputStreamReader(System.in);
        try (var gen = new GenAiSmoke(args[0], System.out::print)){
            BufferedReader in = new BufferedReader(reader);
            String line;
            System.out.print("");
            while ((line = in.readLine()) != null){
                gen.prompt(line);
                System.out.println(", ");

            }
        in.close();
        }
    }

    @Override
    public void close() throws Exception {
        arena.close();
    }
}
