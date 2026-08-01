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
package cn.ypbin.starter.json.dict;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典项。
 *
 * <p>一个字典类型下的一个可选值：{@link #value} 是存储值（code），{@link #label} 是展示文本。
 * 供前端下拉选项与 code→label 翻译使用。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DictItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 存储值（code） */
    private String value;

    /** 展示文本（label） */
    private String label;

    /** 扩展样式标签（如 success/warning，前端标签颜色），可空 */
    private String color;

    /** 排序 */
    private int sort;

    public DictItem() {
    }

    public DictItem(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }
}
