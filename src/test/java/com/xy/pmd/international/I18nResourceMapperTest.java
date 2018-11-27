package com.xy.pmd.international;

import org.junit.Test;

import static org.junit.Assert.*;

public class I18nResourceMapperTest {
    public static final String MESSAGE_KEY_PREFIX = "java.singleton.SingletonShouldHaveOneGetInstanceMethod.violation.msg";

    @Test
    public void getMessage() {
        System.out.println(I18nResourceMapper.getMessage(MESSAGE_KEY_PREFIX));
    }

    @Test
    public void getMessage1() {
        System.out.println(I18nResourceMapper.getMessage(MESSAGE_KEY_PREFIX,I18nResourceMapper.class.getName()));
    }
}