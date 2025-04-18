// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.language.pure.grammar.to;

import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.grammar.from.domain.DateParseTreeWalker;
import org.finos.legend.engine.protocol.pure.m3.function.Function;
import org.finos.legend.engine.protocol.pure.m3.multiplicity.Multiplicity;
import org.finos.legend.engine.protocol.pure.m3.type.generics.GenericType;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.Collection;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.PackageableType;
import org.finos.legend.engine.protocol.pure.m3.type.Type;
import org.finos.legend.engine.protocol.pure.m3.relation.RelationType;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.ValueSpecification;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.Variable;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.AppliedFunction;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CBoolean;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CDateTime;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CDecimal;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CFloat;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CInteger;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CLatestDate;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CStrictDate;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CStrictTime;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.datatype.primitive.CString;
import org.finos.legend.engine.protocol.pure.m3.function.LambdaFunction;
import org.finos.legend.engine.protocol.pure.dsl.path.valuespecification.constant.classInstance.PathElement;
import org.finos.legend.engine.protocol.pure.dsl.path.valuespecification.constant.classInstance.PropertyPathElement;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.classInstance.relation.ColSpec;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.classInstance.relation.ColSpecArray;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerUtility.convertString;
import static org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerUtility.getTabSize;
import static org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerUtility.unsupported;

public class HelperValueSpecificationGrammarComposer
{
    public static final MutableMap<String, String> SPECIAL_INFIX;

    static
    {
        SPECIAL_INFIX = Maps.mutable.empty();
        SPECIAL_INFIX.put("equal", "==");
        SPECIAL_INFIX.put("lessThanEqual", "<=");
        SPECIAL_INFIX.put("lessThan", "<");
        SPECIAL_INFIX.put("greaterThanEqual", ">=");
        SPECIAL_INFIX.put("greaterThan", ">");
        SPECIAL_INFIX.put("plus", "+");
        SPECIAL_INFIX.put("minus", "-");
        SPECIAL_INFIX.put("times", "*");
        SPECIAL_INFIX.put("divide", "/");
        SPECIAL_INFIX.put("and", "&&");
        SPECIAL_INFIX.put("or", "||");
    }

    public static boolean isInfix(AppliedFunction function)
    {
        return SPECIAL_INFIX.get(function.function) != null
                ||
                (function.function.equals("not")
                        &&
                        function.parameters.get(0) instanceof AppliedFunction
                        &&
                        ((AppliedFunction) function.parameters.get(0)).function.equals("equal"));
    }

    public static boolean isPrimitiveValue(ValueSpecification valueSpecification)
    {
        return (valueSpecification instanceof CString ||
                valueSpecification instanceof CBoolean ||
                valueSpecification instanceof CInteger ||
                valueSpecification instanceof CFloat ||
                valueSpecification instanceof CDecimal ||
                valueSpecification instanceof CDateTime ||
                valueSpecification instanceof CStrictDate ||
                valueSpecification instanceof CStrictTime ||
                valueSpecification instanceof CLatestDate
        );
    }

    public static String printColSpec(ColSpec col, DEPRECATED_PureGrammarComposerCore transformer)
    {
        return PureGrammarComposerUtility.convertIdentifier(col.name) + (col.genericType != null ? ":" + printGenericType(col.genericType, transformer) : "") + (col.function1 != null ? ":" + (transformer.isRenderingPretty() ? " " : "") + col.function1.accept(transformer) : "") + (col.function2 != null ? ":" + col.function2.accept(transformer) : "");
    }

    public static String printColSpecArray(ColSpecArray colSpecArray, DEPRECATED_PureGrammarComposerCore transformer)
    {
        StringBuilder builder = new StringBuilder().append("~[");
        if (transformer.isRenderingPretty())
        {
            builder.append(transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(1)) + " ");
        }
        builder.append(LazyIterate.collect(colSpecArray.colSpecs, colSpec -> printColSpec(colSpec, transformer)).makeString("," + (transformer.isRenderingPretty() ? transformer.returnChar() + " " + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(1)) : " ")));
        if (transformer.isRenderingPretty())
        {
            builder.append(transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, 0)).append(" ");
        }
        return builder.append("]").toString();
    }

    public static String renderFunction(AppliedFunction appliedFunction, DEPRECATED_PureGrammarComposerCore transformer)
    {
        List<ValueSpecification> parameters = appliedFunction.parameters;
        String functionName = LazyIterate.collect(FastList.newListWith(appliedFunction.function.split("::")), PureGrammarComposerUtility::convertIdentifier).makeString("::");
        if (parameters.isEmpty())
        {
            return renderFunctionName(functionName, transformer) + "()";
        }
        ValueSpecification firstArgument = parameters.get(0);
        List<ValueSpecification> otherArguments = parameters.subList(1, parameters.size());

        // This is to accommodate for cases where the first parameter is a lambda, such as agg(), col(),
        // it would be wrong to use `->` syntax, e.g. `$x|x.prop1->col()`
        if ((firstArgument instanceof LambdaFunction) || (firstArgument instanceof AppliedFunction && !((AppliedFunction) firstArgument).function.equals("minus") && isInfix((AppliedFunction) firstArgument)))
        {
            return renderFunctionName(functionName, transformer) + "("
                    + (transformer.isRenderingPretty() ? transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(2)) : "")
                    + ListIterate.collect(parameters, p -> p.accept(DEPRECATED_PureGrammarComposerCore.Builder.newInstance(transformer).withIndentation(getTabSize(2)).build()))
                    .makeString("," + (transformer.isRenderingPretty() ? transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(2)) : " "))
                    + (transformer.isRenderingPretty() ? transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(1)) : "") + ")";
        }
        if (otherArguments.isEmpty())
        {
            if (firstArgument instanceof AppliedFunction && !((AppliedFunction) firstArgument).function.equals("minus") && isInfix(((AppliedFunction) firstArgument)))
            {
                return functionName + "(" + firstArgument.accept(transformer) + ")";
            }
            else if (isPrimitiveValue(firstArgument))
            {
                return renderFunctionName(functionName, transformer) + "(" + firstArgument.accept(transformer) + ")";
            }
            return mayWrapInParenthesis(firstArgument, transformer) + (transformer.isRenderingHTML() ? "<span class='pureGrammar-arrow'>" : "") + "->" + (transformer.isRenderingHTML() ? "</span>" : "")
                    + renderFunctionName(functionName, transformer) + "()";
        }
        if (otherArguments.size() == 1 && isPrimitiveValue(otherArguments.get(0)))
        {
            return mayWrapInParenthesis(firstArgument, transformer) + (transformer.isRenderingHTML() ? "<span class='pureGrammar-arrow'>" : "") + "->" + (transformer.isRenderingHTML() ? "</span>" : "")
                    + renderFunctionName(functionName, transformer) + "("
                    + otherArguments.get(0).accept(DEPRECATED_PureGrammarComposerCore.Builder.newInstance(transformer).withIndentation(getTabSize(1)).build())
                    + ")";
        }
        return mayWrapInParenthesis(firstArgument, transformer) + (transformer.isRenderingHTML() ? "<span class='pureGrammar-arrow'>" : "") + "->" + (transformer.isRenderingHTML() ? "</span>" : "")
                + renderFunctionName(functionName, transformer) + "("
                + (transformer.isRenderingPretty() ? transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(1)) : "") +
                ListIterate.collect(otherArguments, p -> p.accept(DEPRECATED_PureGrammarComposerCore.Builder.newInstance(transformer).withIndentation(getTabSize(1)).build()))
                        .makeString("," + (transformer.isRenderingPretty() ? transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(1)) : " "))
                + (transformer.isRenderingPretty() ? transformer.returnChar() + transformer.getIndentationString() : "") + ")";
    }

    private static String mayWrapInParenthesis(ValueSpecification value, DEPRECATED_PureGrammarComposerCore transformer)
    {
        boolean wrap = (value instanceof AppliedFunction && (((AppliedFunction) value).function.equals("minus") || ((AppliedFunction) value).function.equals("not")));
        return (wrap ? "(" : "") + value.accept(transformer) + (wrap ? ")" : "");
    }

    public static String renderFunctionName(String name, DEPRECATED_PureGrammarComposerCore transformer)
    {
        return (transformer.isRenderingHTML() ? "<span class='pureGrammar-function'>" : "") + name + (transformer.isRenderingHTML() ? "</span>" : "");
    }

    public static String possiblyAddParenthesis(String function, ValueSpecification param, DEPRECATED_PureGrammarComposerCore transformer)
    {
        if ("and".equals(function) || "or".equals(function) || "plus".equals(function) || "minus".equals(function) || "times".equals(function) || "divide".equals(function) || "not".equals(function))
        {
            return possiblyAddParenthesis(param, transformer);
        }
        return param.accept(transformer);
    }

    public static String possiblyAddParenthesis(ValueSpecification param, DEPRECATED_PureGrammarComposerCore transformer)
    {
        if (param instanceof AppliedFunction && isInfix((AppliedFunction) param) && (isOneParamAndApplied(((AppliedFunction) param).parameters) || isParamMany(((AppliedFunction) param).parameters)))
        {
            return "(" + param.accept(transformer) + ")";
        }
        return param.accept(transformer);
    }

    private static boolean isOneParamAndApplied(List<ValueSpecification> parameters)
    {
        return parameters.size() == 1 && parameters.get(0) instanceof AppliedFunction;
    }

    private static boolean isParamMany(List<ValueSpecification> parameters)
    {
        return parameters.size() > 1 || (parameters.get(0) instanceof Collection && ((Collection) parameters.get(0)).values.size() > 1);
    }

    public static String renderCollection(List<?> values, org.eclipse.collections.api.block.function.Function<Object, String> func, DEPRECATED_PureGrammarComposerCore transformer)
    {
        if (values.isEmpty())
        {
            return "[]";
        }
        // If there is one entry and the entry is either a primitive value or a variable, we will not create new line
        boolean toCreateNewLine = transformer.isRenderingPretty() &&
                (values.size() != 1 ||
                        !(values.get(0) instanceof ValueSpecification) ||
                        (!isPrimitiveValue((ValueSpecification) values.get(0)) && !(values.get(0) instanceof Variable)));
        return "[" +
                (toCreateNewLine ? transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(1)) : "") +
                LazyIterate.collect(values, func).makeString("," + (transformer.isRenderingPretty() ? transformer.returnChar() + DEPRECATED_PureGrammarComposerCore.computeIndentationString(transformer, getTabSize(1)) : " ")) +
                (toCreateNewLine ? transformer.returnChar() + transformer.getIndentationString() : "") +
                "]";
    }

    public static String renderDecimal(BigDecimal b, DEPRECATED_PureGrammarComposerCore transformer)
    {
        return transformer.isRenderingHTML() ? "<span class='pureGrammar-decimal'>" + b + "d</span>" : b + "D";
    }

    public static String renderString(String s, DEPRECATED_PureGrammarComposerCore transformer)
    {
        String resultString;
        if (transformer.isRenderingHTML())
        {
            resultString = "<span class='pureGrammar-string'>'" + s + "'</span>";
        }
        else if (transformer.isValueSpecificationExternalParameter())
        {
            resultString = s;
        }
        else
        {
            resultString = convertString(s, true);
        }
        return resultString;
    }

    public static String renderBoolean(Boolean b, DEPRECATED_PureGrammarComposerCore transformer)
    {
        return transformer.isRenderingHTML() ? "<span class='pureGrammar-boolean'>" + b + "</span>" : String.valueOf(b);
    }

    public static String renderFloat(Double f, DEPRECATED_PureGrammarComposerCore transformer)
    {
        return transformer.isRenderingHTML() ? "<span class='pureGrammar-float'>" + f + "</span>" : String.valueOf(f);
    }

    public static String renderInteger(Long b, DEPRECATED_PureGrammarComposerCore transformer)
    {
        return transformer.isRenderingHTML() ? "<span class='pureGrammar-integer'>" + b + "</span>" : String.valueOf(b);
    }

    public static String renderDate(String s, DEPRECATED_PureGrammarComposerCore transformer)
    {
        String dateString;
        String updatedS = generateValidDateValueContainingPercent(s);
        if (transformer.isRenderingHTML())
        {
            dateString = "<span class='pureGrammar-datetime'>" + updatedS + "</span>";
        }
        else if (transformer.isValueSpecificationExternalParameter())
        {
            dateString = updatedS.replaceFirst(Character.toString(DateParseTreeWalker.DATE_PREFIX), "").replaceAll(".0000", "");
        }
        else
        {
            dateString = updatedS;
        }
        return dateString;
    }

    public static String renderPathElement(PathElement pathElement, DEPRECATED_PureGrammarComposerCore transformer)
    {
        if (pathElement instanceof PropertyPathElement)
        {
            PropertyPathElement propertyPathElement = (PropertyPathElement) pathElement;
            return (transformer.isRenderingHTML() ? "<span class=pureGrammar-property>" : "") + propertyPathElement.property + (transformer.isRenderingHTML() ? "</span>" : "")
                    + (propertyPathElement.parameters.size() > 1 ? "(" + ListAdapter.adapt(propertyPathElement.parameters).collect(l -> l.accept(transformer)).makeString(", ") + ")" : "");
        }
        return unsupported(pathElement.getClass());
    }

    public static String printFullPath(String fullPath, DEPRECATED_PureGrammarComposerCore transformer)
    {
        if (transformer.isRenderingHTML())
        {
            int index = fullPath.lastIndexOf("::");
            if (index == -1)
            {
                return "<span class='pureGrammar-packageableElement'>" + fullPath + "</span>";
            }
            return "<span class='pureGrammar-package'>" + fullPath.substring(0, index + 2) + "</span><span class='pureGrammar-packageableElement'>" + fullPath.substring(index + 2) + "</span>";
        }
        return fullPath;
    }

    public static String getFunctionDescriptor(Function function)
    {
        StringBuilder builder = new StringBuilder();
        String packageName = function._package;
        String functionName = getFunctionNameWithNoPackage(function);
        String functionSignature = LazyIterate.collect(function.parameters, HelperValueSpecificationGrammarComposer::getFunctionDescriptorParameterSignature).select(Objects::nonNull).makeString(",");
        String returnTypeSignature = getClassSignature(((PackageableType) function.returnGenericType.rawType).fullPath);
        String returnMultiplicitySignature = HelperDomainGrammarComposer.renderMultiplicity(function.returnMultiplicity);
        builder.append(packageName)
                .append("::")
                .append(functionName)
                .append("(")
                .append(functionSignature)
                .append("):")
                .append(returnTypeSignature)
                .append("[")
                .append(returnMultiplicitySignature)
                .append("]");
        return builder.toString();
    }

    public static String getFunctionName(Function fn)
    {
        int signatureIndex = fn.name.indexOf(getFunctionSignature(fn));
        String name = signatureIndex > 0 ? fn.name.substring(0, signatureIndex) : fn.name;
        return fn._package == null || fn._package.isEmpty() ? name : fn._package + "::" + name;
    }

    public static String getFunctionNameWithNoPackage(Function fn)
    {
        int signatureIndex = fn.name.indexOf(getFunctionSignature(fn));
        return signatureIndex > 0 ? fn.name.substring(0, signatureIndex) : fn.name;
    }

    public static String getFunctionSignature(Function function)
    {
        String functionSignature = LazyIterate.collect(function.parameters, HelperValueSpecificationGrammarComposer::getParameterSignature).select(Objects::nonNull).makeString("__")
                + "__" + getClassSignature(((PackageableType) function.returnGenericType.rawType).fullPath) + "_" + getMultiplicitySignature(function.returnMultiplicity) + "_";
        return function.parameters.size() > 0 ? "_" + functionSignature : functionSignature;
    }

    private static String getParameterSignature(Variable p)
    {
        return p.genericType != null ? getClassSignature(((PackageableType) p.genericType.rawType).fullPath) + "_" + getMultiplicitySignature(p.multiplicity) : null;
    }

    private static String getFunctionDescriptorParameterSignature(Variable p)
    {
        return p.genericType != null ? getClassSignature(((PackageableType) p.genericType.rawType).fullPath) + "[" + HelperDomainGrammarComposer.renderMultiplicity(p.multiplicity) + "]" : null;
    }

    private static String getClassSignature(String _class)
    {
        if (_class == null)
        {
            return null;
        }
        return _class.contains("::") ? _class.substring(_class.lastIndexOf("::") + 2) : _class;
    }

    private static String getMultiplicitySignature(Multiplicity multiplicity)
    {
        if (multiplicity.lowerBound == multiplicity.getUpperBoundInt())
        {
            return "" + multiplicity.lowerBound;
        }
        else if (multiplicity.lowerBound == 0 && multiplicity.getUpperBoundInt() == Integer.MAX_VALUE)
        {
            return "MANY";
        }
        return "$" + multiplicity.lowerBound + "_" + (multiplicity.getUpperBoundInt() == Integer.MAX_VALUE ? "MANY" : multiplicity.getUpperBoundInt()) + "$";
    }

    public static String generateValidDateValueContainingPercent(String date)
    {
        return date.indexOf(DateParseTreeWalker.DATE_PREFIX) != -1 ? date : DateParseTreeWalker.DATE_PREFIX + date;
    }

    public static String printGenericType(GenericType genericType, DEPRECATED_PureGrammarComposerCore transformer)
    {
        return printType(genericType.rawType, transformer) +
                (genericType.typeArguments.isEmpty() && genericType.multiplicityArguments.isEmpty() ?
                        "" :
                        "<" +
                                ListIterate.collect(genericType.typeArguments, x -> printGenericType(x, transformer)).makeString(", ") +
                                (genericType.multiplicityArguments.isEmpty() ? "" : "|" + ListIterate.collect(genericType.multiplicityArguments, HelperDomainGrammarComposer::renderMultiplicity).makeString(",")) +
                                ">"
                ) +
                ((genericType.typeVariableValues.isEmpty()) ? "" : "(" + ListIterate.collect(genericType.typeVariableValues, x -> x.accept(transformer)).makeString(",") + ")");
    }

    public static String printType(Type type, DEPRECATED_PureGrammarComposerCore transformer)
    {
        if (type instanceof PackageableType)
        {
            return printFullPath(((PackageableType) type).fullPath, transformer);
        }
        else if (type instanceof RelationType)
        {
            return "(" + ListIterate.collect(((RelationType) type).columns, x -> PureGrammarComposerUtility.convertIdentifier(x.name) + ":" + printGenericType(x.genericType, transformer) + (x.multiplicity == null || Multiplicity.ZERO_ONE.equals(x.multiplicity) ? "" : "[" + HelperDomainGrammarComposer.renderMultiplicity(x.multiplicity) + "]")).makeString(", ") + ")";
        }
        throw new RuntimeException(type.getClass().getSimpleName() + ": Not supported");
    }
}
