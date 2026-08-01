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

import java.util.List;

/**
 * 字典数据来源扩展点。
 *
 * <p>starter 只定义查询契约，字典表与 CRUD 由业务系统实现（如 sys_dict / sys_dict_item）。
 * 业务方实现本接口把数据库字典接进来，{@link DictCache} 会加缓存，{@code @DictText} 与
 * {@link DictUtils} 据此翻译 code→label。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface DictProvider {

    /**
     * 获取指定字典类型下的全部字典项。
     *
     * @param dictType 字典类型编码
     * @return 字典项列表，不存在时返回空列表
     */
    List<DictItem> getItems(String dictType);
}
