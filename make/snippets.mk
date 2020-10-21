INCLUDE_SNIPPETS := $(PWD)/snippets/target/graalvm-native-image/include-snippets

$(INCLUDE_SNIPPETS):
	cd "snippets"
	sbt --java-home "/usr/lib/jvm/java-11-graalvm" "show graalvm-native-image:packageBin"

.PHONY: snippets
snippets: snippets/target/graalvm-native-image/include-snippets

.PHONY: snippets-clean
snippets-clean:
	cd "snippets"
	sbt --java-home "/usr/lib/jvm/java-11-graalvm" "clean"