package jnu.econovation.isekai.common.s3.exception

import org.springframework.core.NestedRuntimeException

class FileDownloadFailedException(msg: String) : NestedRuntimeException(msg)