//javac -d out $(find panama-src -name '*.java')
//javac -cp out -d out src/main/java/demo/GenAiSmoke.java
//rm -rf out && mkdir -p out


docker run -it --rm \
-v "$(pwd)":/workspace \
-v "$(pwd)/models":/models \
panama-genai \
java -cp /workspace/out \
--enable-native-access=ALL-UNNAMED \
main.java.demo.GenAiSmoke /models/deps/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4