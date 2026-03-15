package main.java.demo;

import ffi.genai.ort_genai_c_h;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.lang.foreign.ValueLayout;

import static ffi.genai.ort_genai_c_h.*;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class GenAiSmoke implements AutoCloseable {

    static final String PROMPT_TEMPLATE = """
            <User>
            %s<|end|>
            <|assistant|>""";

    private final Arena arena;
    private final MemorySegment ret;
    private final MemorySegment model;
    private final MemorySegment tokenizer;
    private final MemorySegment tokenizerStream;
    private final MemorySegment generatorParams;
    private final MemorySegment count;
    private final Consumer<String> out;

    public GenAiSmoke(String modelPath, Consumer<String> out) {
        Path cfg = Path.of(modelPath, "genai_config.json");
        if (!Files.isRegularFile(cfg)) {
            throw new IllegalArgumentException(
                    "Model directory must contain genai_config.json -> " + cfg);
        }

        arena = Arena.ofConfined();
        ret = arena.allocate(ValueLayout.ADDRESS);


        this.out = out;

        model = call(OgaCreateModel(arena.allocateFrom(modelPath), ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyModel);
        tokenizer = call(OgaCreateTokenizer(model, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyTokenizer);
        tokenizerStream = call(OgaCreateTokenizerStream(tokenizer, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyTokenizerStream);
        generatorParams = call(OgaCreateGeneratorParams(model, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyGeneratorParams);
        call(OgaGeneratorParamsSetSearchNumber(generatorParams, arena.allocateFrom("max_length"), 512));
        count = arena.allocate(ValueLayout.JAVA_LONG);
    }

    private MemorySegment call(MemorySegment status) {
        try {
            if (!status.equals(MemorySegment.NULL)) {
                if (status.get(JAVA_INT, 0) != 0) {
                    MemorySegment errPtr = OgaResultGetError(status);
                    String msg = (errPtr == null || errPtr.equals(MemorySegment.NULL))
                            ? "unknown"
                            : errPtr.reinterpret(Integer.MAX_VALUE).getString(0);
                    OgaDestroyResult(status);
                    throw new RuntimeException("failed: " + msg);
                }
            }
            return ret.get(ValueLayout.ADDRESS, 0).reinterpret(Integer.MAX_VALUE);
        } finally {
            OgaDestroyResult(status);
        }
    }


    public int prompt(String prompt) {
        MemorySegment currentGenerator = call(OgaCreateGenerator(model, generatorParams, ret))
                .reinterpret(arena, ort_genai_c_h::OgaDestroyGenerator);
        int tokens = 0;
        var inputTokens = MemorySegment.NULL;
        try {
            inputTokens = call(OgaCreateSequences(ret));

            String formattedPrompt = PROMPT_TEMPLATE.formatted(prompt);
            call(OgaTokenizerEncode(tokenizer, arena.allocateFrom(formattedPrompt), inputTokens));
            call(OgaGenerator_AppendTokenSequences(currentGenerator, inputTokens));

            while (!OgaGenerator_IsDone(currentGenerator)) {
                call(OgaGenerator_GenerateNextToken(currentGenerator));

                var memSegment = call(OgaGenerator_GetNextTokens(currentGenerator, ret, count));

                long numTokens = count.get(ValueLayout.JAVA_LONG, 0);

                if (numTokens > 0) {
                    var resizedSegment = memSegment.reinterpret(numTokens * JAVA_INT.byteSize());

                    int nextToken = resizedSegment.get(JAVA_INT, 0);

                    MemorySegment decodedSegment = call(OgaTokenizerStreamDecode(tokenizerStream, nextToken, ret));
                    String response = decodedSegment.reinterpret(Integer.MAX_VALUE).getString(0);
                    out.accept(response);
                    System.out.flush();
                    tokens++;

                }
            }
            out.accept("\n");
            return tokens;
        } catch (Throwable t) {
            t.printStackTrace();
            return -1;
        } finally {
            if (!inputTokens.equals(MemorySegment.NULL)) {
                OgaDestroySequences(inputTokens);
                OgaDestroyGenerator(currentGenerator);
            }

        }

    }

    public static void main(String[] args) throws Exception {
        Reader reader = new InputStreamReader(System.in);
        try (var gen = new GenAiSmoke(args[0], System.out::print)) {
            BufferedReader in = new BufferedReader(reader);
            String userPrompt;
            System.out.print("> ");
            while ((userPrompt = in.readLine()) != null) {
                gen.prompt(userPrompt);
                System.out.println("> ");

            }
            in.close();
        }
    }

    @Override
    public void close() throws Exception {
        arena.close();
    }
}
