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
package cn.ypbin.starter.core.util;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文静态持有工具。
 *
 * <p>在非托管对象（如静态方法、工具类）中获取 Bean、发布事件、读取配置。
 * 同时实现 {@link ApplicationContextAware} 与 {@link BeanFactoryPostProcessor}，
 * 兼顾容器就绪后与刷新早期两个阶段的可用性。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Component
public class SpringUtils implements ApplicationContextAware, BeanFactoryPostProcessor {

    private static ApplicationContext applicationContext;

    private static ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory factory) throws BeansException {
        beanFactory = factory;
    }

    /**
     * 获取应用上下文。
     *
     * @return {@link ApplicationContext}
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 按类型获取 Bean。
     *
     * @param requiredType Bean 类型
     * @param <T>          泛型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> requiredType) {
        return beanFactory != null ? beanFactory.getBean(requiredType) : applicationContext.getBean(requiredType);
    }

    /**
     * 按名称与类型获取 Bean。
     *
     * @param name         Bean 名称
     * @param requiredType Bean 类型
     * @param <T>          泛型
     * @return Bean 实例
     */
    public static <T> T getBean(String name, Class<T> requiredType) {
        return beanFactory != null ? beanFactory.getBean(name, requiredType) : applicationContext.getBean(name, requiredType);
    }

    /**
     * 判断是否包含指定名称的 Bean。
     *
     * @param name Bean 名称
     * @return 是否存在
     */
    public static boolean containsBean(String name) {
        return beanFactory != null && beanFactory.containsBean(name);
    }

    /**
     * 发布应用事件。
     *
     * @param event 事件对象
     */
    public static void publishEvent(Object event) {
        if (applicationContext != null) {
            applicationContext.publishEvent(event);
        }
    }

    /**
     * 获取事件发布器。
     *
     * @return {@link ApplicationEventPublisher}
     */
    public static ApplicationEventPublisher getEventPublisher() {
        return applicationContext;
    }

    /**
     * 获取环境配置对象。
     *
     * @return {@link Environment}
     */
    public static Environment getEnvironment() {
        return applicationContext.getEnvironment();
    }

    /**
     * 读取配置项。
     *
     * @param key 配置键
     * @return 配置值，不存在时返回 {@code null}
     */
    public static String getProperty(String key) {
        return getEnvironment().getProperty(key);
    }

    /**
     * 获取当前激活的 profiles。
     *
     * @return profile 数组
     */
    public static String[] getActiveProfiles() {
        return getEnvironment().getActiveProfiles();
    }

    /**
     * 优雅关闭应用上下文。
     */
    public static void closeContext() {
        if (applicationContext instanceof ConfigurableApplicationContext ctx) {
            ctx.close();
        }
    }
}
