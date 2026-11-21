#include <jni.h>
#include <cstring>
#include <string>
#include <vector>
#include "node.h"

extern "C" JNIEXPORT jint JNICALL
Java_com_reimagined_winner_MainActivity_startNodeWithArguments(
        JNIEnv *env, jobject /* instance */, jobjectArray arguments) {
    const jsize count = env->GetArrayLength(arguments);
    std::vector<std::string> values;
    values.reserve(static_cast<size_t>(count));
    size_t bufferSize = 0;
    for (jsize i = 0; i < count; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        const char *utf = env->GetStringUTFChars(item, nullptr);
        values.emplace_back(utf ? utf : "");
        bufferSize += values.back().size() + 1;
        if (utf) env->ReleaseStringUTFChars(item, utf);
        env->DeleteLocalRef(item);
    }
    // node::Start/libuv require both argv and all argument strings to use one
    // contiguous, writable memory block (as in the Node.js Mobile Android guide).
    std::vector<char> buffer(bufferSize, '\0');
    std::vector<char *> argv;
    argv.reserve(static_cast<size_t>(count));
    size_t offset = 0;
    for (const auto &value : values) {
        char *slot = buffer.data() + offset;
        std::memcpy(slot, value.c_str(), value.size() + 1);
        argv.push_back(slot);
        offset += value.size() + 1;
    }
    return static_cast<jint>(node::Start(static_cast<int>(argv.size()), argv.data()));
}
