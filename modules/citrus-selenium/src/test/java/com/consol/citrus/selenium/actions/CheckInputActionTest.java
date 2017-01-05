/*
 * Copyright 2006-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.selenium.actions;

import com.consol.citrus.selenium.endpoint.SeleniumBrowser;
import com.consol.citrus.testng.AbstractTestNGUnitTest;
import org.mockito.Mockito;
import org.openqa.selenium.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;

/**
 * @author Christoph Deppisch
 * @since 2.7
 */
public class CheckInputActionTest extends AbstractTestNGUnitTest {

    private SeleniumBrowser seleniumBrowser = new SeleniumBrowser();
    private WebDriver webDriver = Mockito.mock(WebDriver.class);
    private WebElement element = Mockito.mock(WebElement.class);

    private CheckInputAction action;

    @BeforeMethod
    public void setup() {
        reset(webDriver, element);

        seleniumBrowser.setWebDriver(webDriver);

        action =  new CheckInputAction();
        action.setBrowser(seleniumBrowser);

        action.setSelectorType("name");
        action.setSelect("checkbox");

        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(true);
        when(element.getTagName()).thenReturn("input");
    }

    @Test
    public void testExecuteCheck() throws Exception {
        when(webDriver.findElement(any(By.class))).thenReturn(element);

        when(element.isSelected()).thenReturn(false);
        action.setChecked(true);

        action.execute(context);

        verify(element).click();
    }

    @Test
    public void testExecuteUncheck() throws Exception {
        when(webDriver.findElement(any(By.class))).thenReturn(element);

        when(element.isSelected()).thenReturn(true);
        action.setChecked(false);

        action.execute(context);

        verify(element).click();
    }

    @Test
    public void testExecuteAlreadyChecked() throws Exception {
        when(webDriver.findElement(any(By.class))).thenReturn(element);

        when(element.isSelected()).thenReturn(true);
        action.setChecked(true);

        action.execute(context);

        verify(element, times(0)).click();
    }

}