/*
 * 本地大模型 JNI 封装（MNN 3.6.2 LLM 引擎）。
 *
 * 产物 libmnnllmjni.so 直接放进 src/main/jniLibs/<abi>/，不在 Gradle 里挂
 * externalNativeBuild —— 本机的 CMake 在探测 NDK 编译器时会崩溃（3.22.1 与
 * 3.31.6 都一样），而这里只有一个源文件，用 NDK clang 直接编更省事：
 *
 *   clang++ --target=aarch64-linux-android24 -fPIC -shared -std=c++17 -O2 \
 *     -I app/src/main/cpp/include app/src/main/cpp/mnn_llm_jni.cpp \
 *     -o app/src/main/jniLibs/arm64-v8a/libmnnllmjni.so \
 *     jniLibs/arm64-v8a/libllm.so libMNN_Express.so libMNN_CL.so libMNN.so -llog
 *
 * v7a 把 target 换成 armv7a-linux-androideabi24、路径换成 armeabi-v7a 即可。
 */

#include <jni.h>
#include <sstream>
#include <string>

#include <llm/llm.hpp>

namespace {

inline MNN::Transformer::Llm* toLlm(jlong ptr) {
    return reinterpret_cast<MNN::Transformer::Llm*>(ptr);
}

} // namespace

/**
 * Kotlin 侧的薄封装：只暴露 create / load / generate / release 四个动作。
 *
 * 刻意不做异步与流式：MNN 的 response 本身是阻塞的，跨线程回调还要处理
 * getenv / AttachCurrentThread，第一版先保证「一次问、一次答」能跑通，
 * 上层用协程切到 IO 线程即可，不会卡 UI。
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_my_data_ai_MnnLlmBridge_nativeCreate(JNIEnv* env, jobject /*thiz*/, jstring jConfigPath) {
    if (jConfigPath == nullptr) return 0;
    const char* raw = env->GetStringUTFChars(jConfigPath, nullptr);
    std::string path(raw);
    env->ReleaseStringUTFChars(jConfigPath, raw);
    auto* llm = MNN::Transformer::Llm::createLLM(path);
    return reinterpret_cast<jlong>(llm);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_my_data_ai_MnnLlmBridge_nativeLoad(JNIEnv* /*env*/, jobject /*thiz*/, jlong ptr) {
    auto* llm = toLlm(ptr);
    if (llm == nullptr) return JNI_FALSE;
    return llm->load() ? JNI_TRUE : JNI_FALSE;
}

/**
 * 生成回答。三个坑必须都填上，缺一就会出现「答非所问 / 自问自答」：
 *
 * 1. reset()：session 是复用的，上一轮的 KV cache 不清掉，模型会把新消息
 *    当成旧对话的续写，内容直接跑偏；
 * 2. 手拼 Qwen2.5 的 ChatML 模板：不能裸喂文本，裸文本会被当成普通语料续写
 *    （比如自己编个问题再自己回答）；
 * 3. end_with 传 "<|im_end|>"：不给停止符模型永远不知道在哪收尾。
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_my_data_ai_MnnLlmBridge_nativeGenerate(
    JNIEnv* env, jobject /*thiz*/, jlong ptr, jstring jPrompt, jint jMaxTokens) {
    auto* llm = toLlm(ptr);
    if (llm == nullptr || jPrompt == nullptr) return nullptr;

    const char* raw = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(raw);
    env->ReleaseStringUTFChars(jPrompt, raw);

    llm->reset();

    std::ostringstream full;
    full << "<|im_start|>system\n"
         << "你是「MY足迹」旅行 App 里的 AI 助手，用简洁自然的中文回答。"
            "回答聚焦旅行相关的话题；话题无关时礼貌地把话带回旅行。<|im_end|>\n"
         << "<|im_start|>user\n" << prompt << "<|im_end|>\n"
         << "<|im_start|>assistant\n";

    std::ostringstream out;
    llm->response(full.str(), &out, "<|im_end|>", static_cast<int>(jMaxTokens));

    std::string text = out.str();
    // 防御性 strip：有的 MNN 版本会把停止符本身写进输出流
    const std::string stop = "<|im_end|>";
    auto pos = text.find(stop);
    if (pos != std::string::npos) text.erase(pos);
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_my_data_ai_MnnLlmBridge_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/, jlong ptr) {
    auto* llm = toLlm(ptr);
    if (llm != nullptr) MNN::Transformer::Llm::destroy(llm);
}
