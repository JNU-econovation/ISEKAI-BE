package jnu.econovation.isekai.common.s3.enums

enum class FileName(val text: String, private val extension: FileExtension) {
    LIVE2D_MODEL_FILE_NAME("live2d_model", extension = FileExtension.ZIP),
    BACKGROUND_IMAGE_FILE_NAME("background_image", extension = FileExtension.PNG);

    override fun toString(): String = "${this.text}.${this.extension.text}"

    fun getExtensionValue(): String = this.extension.text

    private enum class FileExtension(val text: String) {
        ZIP("zip"),
        PNG("png");
    }
}