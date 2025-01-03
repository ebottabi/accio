/*
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

package io.accio.base.jinjava;

import io.accio.base.dto.Macro;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static io.accio.base.dto.Macro.macro;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class TestJinjavaExpressionProcessor
{
    List<Macro> macros = List.of(
            macro("standardTime", "() => standardTime"),
            macro("callStandardTime", "() => {{ standardTime() }}"),
            macro("passMacroWithoutParam", "(rule: Macro) => {{ rule() }}"),
            macro("addOne", "(a: Expression) => {{ a }} + 1"),
            macro("addTwo", "(a: Expression) => {{ a }} + 2"),
            macro("callAddOne", "(a: Expression) => {{ addOne(a) }} + 3"),
            macro("passMacro", "(a: Expression, f: Macro) => {{ f(a) }} + 4"),
            macro("pass2Macro", "(a: Expression, f1: Macro, f2: Macro) => {{ f1(a) + f2(a) }} + 5"),
            macro("pass3Macro", "(a: Expression, b: Expression, f1: Macro, f2: Macro) => {{ f1(a) + f2(b) }} + 6"),
            macro("pass4Macro", "(a: Expression, b: Expression, f1: Macro, f2: Macro) => {{ f1(a) }} + {{ f2(b)}}"));

    @DataProvider
    public Object[][] macroCall()
    {
        return new Object[][] {
                {"{{ addOne(1) }}", "{{ addOne(1) }}"},
                {"{{ callAddOne(1) }}", "{{ callAddOne(1) }}"},
                {"{{ passMacro(1, addOne) }}", "{{addOne(1)}} + 4"},
                {"{{ pass2Macro(1, addOne, addTwo) }}", "{{(addOne(1) + addTwo(1))}} + 5"},
                {"{{ pass3Macro(1, 2, addOne, addTwo) }}", "{{(addOne(1) + addTwo(2))}} + 6"},
                {"{{ pass4Macro(1, 2, addOne, addTwo) }}", "{{addOne(1)}} + {{addTwo(2)}}"},
                // TODO: trim the redundant space character
                {"{{ passMacro(1, addOne) }} + {{ passMacro(2, addTwo) }}", "{{addOne(1)}} + 4  +  {{addTwo(2)}} + 4"},
                {"{{ passMacro(1, addOne) }} + {{ addOne(1) }}", "{{addOne(1)}} + 4  + {{ addOne(1) }}"},
                {"{{ standardTime() }}", "{{ standardTime() }}"},
                {"{{ callStandardTime() }}", "{{ callStandardTime() }}"},
                {"{{ passMacroWithoutParam(standardTime) }}", "{{standardTime()}}"}};
        // TODO: unsupported cases: A jinjava expression includes multiple macro calls
        // {"{{ passMacro(1, addOne) + addOne(1) }}", "{{addOne(1)}} + 4 + {{addOne(1)}}"}
        // {"{{ passMacro(1, addOne) + passMacro(2, addTwo) }}", "{{addOne(1)}} + 4 + {{addTwo(2)}} + 4"}
    }

    @Test(dataProvider = "macroCall")
    public void testProcessExpression(String expression, String expected)
    {
        assertThat(JinjavaExpressionProcessor.process(expression, macros).trim()).isEqualTo(expected);
    }
}
