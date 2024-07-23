# JFastSpell
JFastSpell is a Java wrapper of [FastSpell](https://github.com/mbanon/fastspell/), the most performant, publicly available language detection module by 07/23/2024.

I have no prior knowledge of Java. Please let me know if you have any issue.

## Dependencies
- [Bazel](https://bazel.build/start/java)
- [Gson](https://github.com/google/gson)
- [JFastText](https://github.com/vinhkhuc/JFastText/)
- [HunSpell JNA](https://gitlab.com/dumonts/hunspell-java/-/tree/master) ([This](https://github.com/dren-dk/HunspellJNA) might also work, but it hasn't been maintained for 7 years.)

## Setup
1. Download `lid.176.bin` model from [FastText](https://fasttext.cc/docs/en/language-identification.html) and put it in the `src/main/resources` folder of your project.

2. Download the `fastspell_dictionaries` [folder](https://github.com/mbanon/fastspell-dictionaries/tree/master/src/fastspell_dictionaries) and put it in the `src/main/resources` folder of your project. Make sure the folder name uses an underscore `_` instead of `-`.

3. Make sure that the contents of `hunspellCodes.json` and `similarLangs.json` in the `src/main/resources` folder matches the contents of the `.yaml` configurations [here](https://github.com/mbanon/fastspell/tree/main/src/fastspell/config).

4. Build the aforementioned dependencies and put the `.jar` files in the `dependencies` folder at project root:
```
gson-2.11.0.jar
hunspell-2.2.0.jar
jfasttext-0.5-jar-with-dependencies.jar
```

5. (optional) Modify your configurations in `src/main/resources/config.json` if necessary.

## Build
Make sure that you have successfully installed Bazel. Go to the project root directory. Build FastSpell:
```
bazel build //:FastSpell
```

Then test it with an arbitrary input sentence:
```
bazel-bin/FastSpell YOUR_SENTENCE_HERE
```

## Usage
```java
FastSpell fs = new FastSpell();
String lang = fs.getLang("Hello world!");
System.out.println(lang);
```