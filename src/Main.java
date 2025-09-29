
void main() {
  IO.println("Hello and welcome!");
  for (int i = 1; i <= 5; i++) {
    IO.println("i = " + i);
  }
}



//# inside the container
//./scripts/jextract-gen.sh

//That’s it—your generated Java sources will be in panama-src/ort and panama-src/genai on your host.

//# inside the container
//mkdir -p /workspace/build/classes
//javac -d /workspace/build/classes \
//  $(find /workspace/panama-src -name '*.java') \
//  /workspace/src/main/java/demo/GenAiSmoke.java
//

//# run (mount your ORT-GenAI model at /models/deps when starting the container)
//# inside the container:
//java -cp /workspace/build/classes -DmodelDir=/models/deps demo.GenAiSmoke

//rm -f /usr/local/bin/jextract
//cat >/usr/local/bin/jextract <<'SH'
//#!/usr/bin/env bash
//exec /opt/jextract/bin/jextract "$@"
//SH
//chmod +x /usr/local/bin/jextract
//jextract --version