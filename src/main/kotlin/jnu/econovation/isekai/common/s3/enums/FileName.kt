package jnu.econovation.isekai.common.s3.enums

enum class FileName(val text: String, private val extension: FileExtension) {
    LIVE2D_MODEL_FILE_NAME("live2d_model", extension = FileExtension.ZIP),
    LIVE2D_MODEL_NUKKI_FILE_NAME("live2d_model_nukki", FileExtension.PNG),
    BACKGROUND_IMAGE_FILE_NAME("background_image", extension = FileExtension.PNG),
    THUMBNAIL_IMAGE_FILE_NAME("thumbnail_image", extension = FileExtension.PNG);

    override fun toString(): String = "${this.text}.${this.extension.text}"

    fun getExtensionValue(): String = this.extension.text

    private enum class FileExtension(val text: String) {
        ZIP("zip"),
        PNG("png");
    }
}