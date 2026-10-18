/*
 * Copyright (c) 2024-present ypbin-starter authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.starter.tools.csv;

import java.io.Serial;
import java.util.List;

/**
 * CSV 解析失败（文件级或严格读取时的行级错误聚合）。
 *
 * <p>继承 {@link RuntimeException}：解析失败是调用方传入了无法处理的输入，属可预期的失败路径，
 * 若声明为受检异常会迫使每个调用点写无意义的 {@code try/catch}。调用方需要逐行处理时改用
 * {@link CsvReader#parse}，它会返回 {@link CsvDocument#errors()} 而不抛异常。</p>
 *
 * <p>{@link #getErrors()} 在文件级失败时为空列表（不是 null）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public class CsvParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误码（稳定标识，可直接映射业务侧提示）。 */
    private final CsvErrorCode errorCode;

    /** 行级错误明细（文件级失败时为空列表）。 */
    private final List<CsvRowError> errors;

    /**
     * 文件级失败。
     *
     * @param errorCode 错误码
     * @param message   说明
     */
    public CsvParseException(CsvErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null);
    }

    /**
     * 文件级失败（带原因）。
     *
     * @param errorCode 错误码
     * @param message   说明
     * @param cause     原因
     */
    public CsvParseException(CsvErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, List.of(), cause);
    }

    /**
     * 行级错误聚合失败。
     *
     * @param errorCode 错误码
     * @param message   说明（含首个错误）
     * @param errors    行级错误明细（不可为 null）
     */
    public CsvParseException(CsvErrorCode errorCode, String message, List<CsvRowError> errors) {
        this(errorCode, message, errors, null);
    }

    /**
     * 完整构造。
     *
     * @param errorCode 错误码
     * @param message   说明
     * @param errors    行级错误明细（不可为 null）
     * @param cause     原因（可为 null）
     */
    public CsvParseException(CsvErrorCode errorCode, String message, List<CsvRowError> errors, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errors = List.copyOf(errors);
    }

    /**
     * 错误码。
     *
     * @return 错误码
     */
    public CsvErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 行级错误明细。
     *
     * @return 错误明细（文件级失败时为空列表，<b>不为 null</b>）
     */
    public List<CsvRowError> getErrors() {
        return errors;
    }
}
