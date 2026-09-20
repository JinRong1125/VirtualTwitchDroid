package com.example.virtualtwitchdroid.feature.avatar.rig

/**
 * The VRM 1.0 (`VRMC_vrm`) expression **presets** we drive. [key] is the preset name as it appears in
 * the extension JSON (`expressions.preset.<key>`). Emotions and visemes may co-fire; how they combine
 * (VRM's `overrideMouth` etc.) is applied when the rig is bound to a model, not here.
 */
enum class VrmExpression(val key: String) {
    // Emotions
    HAPPY("happy"),
    ANGRY("angry"),
    SAD("sad"),
    RELAXED("relaxed"),
    SURPRISED("surprised"),

    // Visemes (lip sync)
    AA("aa"),
    IH("ih"),
    OU("ou"),
    EE("ee"),
    OH("oh"),

    // Blink
    BLINK("blink"),
    BLINK_LEFT("blinkLeft"),
    BLINK_RIGHT("blinkRight"),

    // Gaze
    LOOK_UP("lookUp"),
    LOOK_DOWN("lookDown"),
    LOOK_LEFT("lookLeft"),
    LOOK_RIGHT("lookRight"),

    NEUTRAL("neutral"),
}
