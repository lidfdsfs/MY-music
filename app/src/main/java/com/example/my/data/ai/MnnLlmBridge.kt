package com.example.my.data.ai

/**
 * MNN LLM 的 JNI 入口（对应 src/main/cpp/mnn_llm_jni.cpp）。
 *
 * 只做四件事：建会话、加载模型、生成、释放。真正的推理在 libllm.so 里，
 * 这里不复制官方 MnnLlmChat 那一整套 Kotlin 封装（它依赖 ChatService、Jinja 等十几个类），
 * 保持依赖面最小，坏了一看就知道是哪一层。
 */
object MnnLlmBridge {

    /** 原生库是否加载成功：ABI 不匹配或 .so 缺失都会在这里暴露 */
    val available: Boolean = runCatching { System.loadLibrary("mnnllmjni") }.isSuccess

    external fun nativeCreate(configPath: String): Long

    external fun nativeLoad(session: Long): Boolean

    external fun nativeGenerate(session: Long, prompt: String, maxTokens: Int): String?

    external fun nativeRelease(session: Long)
}
