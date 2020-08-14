/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.engine;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLXML;
import java.util.ArrayList;
import java.util.Arrays;

import org.h2.Driver;
import org.h2.api.ErrorCode;
import org.h2.command.Parser;
import org.h2.expression.Expression;
import org.h2.jdbc.JdbcArray;
import org.h2.jdbc.JdbcBlob;
import org.h2.jdbc.JdbcClob;
import org.h2.jdbc.JdbcConnection;
import org.h2.jdbc.JdbcLob;
import org.h2.jdbc.JdbcResultSet;
import org.h2.jdbc.JdbcSQLXML;
import org.h2.message.DbException;
import org.h2.message.Trace;
import org.h2.schema.Schema;
import org.h2.schema.SchemaObjectBase;
import org.h2.table.Table;
import org.h2.util.JdbcUtils;
import org.h2.util.SourceCompiler;
import org.h2.util.StringUtils;
import org.h2.util.Utils;
import org.h2.value.DataType;
import org.h2.value.TypeInfo;
import org.h2.value.Value;
import org.h2.value.ValueNull;

/**
 * Represents a user-defined function, or alias.
 *
 * @author Thomas Mueller
 * @author Gary Tong
 */
public class FunctionAlias extends SchemaObjectBase {

    private String className;
    private String methodName;
    private String source;
    private JavaMethod[] javaMethods;
    private boolean deterministic;

    private FunctionAlias(Schema schema, int id, String name) {
        super(schema, id, name, Trace.FUNCTION);
    }

    /**
     * Create a new alias based on a method name.
     *
     * @param schema the schema
     * @param id the id
     * @param name the name
     * @param javaClassMethod the class and method name
     * @param force create the object even if the class or method does not exist
     * @return the database object
     */
    public static FunctionAlias newInstance(
            Schema schema, int id, String name, String javaClassMethod,
            boolean force) {
        FunctionAlias alias = new FunctionAlias(schema, id, name);
        int paren = javaClassMethod.indexOf('(');
        int lastDot = javaClassMethod.lastIndexOf('.', paren < 0 ?
                javaClassMethod.length() : paren);
        if (lastDot < 0) {
            throw DbException.get(ErrorCode.SYNTAX_ERROR_1, javaClassMethod);
        }
        alias.className = javaClassMethod.substring(0, lastDot);
        alias.methodName = javaClassMethod.substring(lastDot + 1);
        alias.init(force);
        return alias;
    }

    /**
     * Create a new alias based on source code.
     *
     * @param schema the schema
     * @param id the id
     * @param name the name
     * @param source the source code
     * @param force create the object even if the class or method does not exist
     * @return the database object
     */
    public static FunctionAlias newInstanceFromSource(
            Schema schema, int id, String name, String source, boolean force) {
        FunctionAlias alias = new FunctionAlias(schema, id, name);
        alias.source = source;
        alias.init(force);
        return alias;
    }

    private void init(boolean force) {
        try {
            // at least try to compile the class, otherwise the data type is not
            // initialized if it could be
            load();
        } catch (DbException e) {
            if (!force) {
                throw e;
            }
        }
    }

    private synchronized void load() {
        if (javaMethods != null) {
            return;
        }
        if (source != null) {
            loadFromSource();
        } else {
            loadClass();
        }
    }

    private void loadFromSource() {
        SourceCompiler compiler = database.getCompiler();
        synchronized (compiler) {
            String fullClassName = Constants.USER_PACKAGE + "." + getName();
            compiler.setSource(fullClassName, source);
            try {
                Method m = compiler.getMethod(fullClassName);
                JavaMethod method = new JavaMethod(m, 0);
                javaMethods = new JavaMethod[] {
                        method
                };
            } catch (DbException e) {
                throw e;
            } catch (Exception e) {
                throw DbException.get(ErrorCode.SYNTAX_ERROR_1, e, source);
            }
        }
    }

    private void loadClass() {
        Class<?> javaClass = JdbcUtils.loadUserClass(className);
        Method[] methods = javaClass.getMethods();
        ArrayList<JavaMethod> list = new ArrayList<>(1);
        for (int i = 0, len = methods.length; i < len; i++) {
            Method m = methods[i];
            if (!Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (m.getName().equals(methodName) ||
                    getMethodSignature(m).equals(methodName)) {
                JavaMethod javaMethod = new JavaMethod(m, i);
                for (JavaMethod old : list) {
                    if (old.getParameterCount() == javaMethod.getParameterCount()) {
                        throw DbException.get(ErrorCode.
                                METHODS_MUST_HAVE_DIFFERENT_PARAMETER_COUNTS_2,
                                old.toString(), javaMethod.toString());
                    }
                }
                list.add(javaMethod);
            }
        }
        if (list.isEmpty()) {
            throw DbException.get(
                    ErrorCode.PUBLIC_STATIC_JAVA_METHOD_NOT_FOUND_1,
                    methodName + " (" + className + ")");
        }
        javaMethods = list.toArray(new JavaMethod[0]);
        // Sort elements. Methods with a variable number of arguments must be at
        // the end. Reason: there could be one method without parameters and one
        // with a variable number. The one without parameters needs to be used
        // if no parameters are given.
        Arrays.sort(javaMethods);
    }

    private static String getMethodSignature(Method m) {
        StringBuilder buff = new StringBuilder(m.getName());
        buff.append('(');
        Class<?>[] parameterTypes = m.getParameterTypes();
        for (int i = 0, length = parameterTypes.length; i < length; i++) {
            if (i > 0) {
                // do not use a space here, because spaces are removed
                // in CreateFunctionAlias.setJavaClassMethod()
                buff.append(',');
            }
            Class<?> p = parameterTypes[i];
            if (p.isArray()) {
                buff.append(p.getComponentType().getName()).append("[]");
            } else {
                buff.append(p.getName());
            }
        }
        return buff.append(')').toString();
    }

    @Override
    public String getCreateSQLForCopy(Table table, String quotedName) {
        throw DbException.throwInternalError(toString());
    }

    @Override
    public String getDropSQL() {
        return "DROP ALIAS IF EXISTS " + getSQL(DEFAULT_SQL_FLAGS);
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, int sqlFlags) {
        // TODO can remove this method once FUNCTIONS_IN_SCHEMA is enabled
        if (database.getSettings().functionsInSchema || getSchema().getId() != Constants.MAIN_SCHEMA_ID) {
            return super.getSQL(builder, sqlFlags);
        }
        return Parser.quoteIdentifier(builder, getName(), sqlFlags);
    }

    @Override
    public String getCreateSQL() {
        StringBuilder buff = new StringBuilder("CREATE FORCE ALIAS ");
        buff.append(getSQL(DEFAULT_SQL_FLAGS));
        if (deterministic) {
            buff.append(" DETERMINISTIC");
        }
        if (source != null) {
            buff.append(" AS ");
            StringUtils.quoteStringSQL(buff, source);
        } else {
            buff.append(" FOR ");
            Parser.quoteIdentifier(buff, className + '.' + methodName, DEFAULT_SQL_FLAGS);
        }
        return buff.toString();
    }

    @Override
    public int getType() {
        return DbObject.FUNCTION_ALIAS;
    }

    @Override
    public synchronized void removeChildrenAndResources(Session session) {
        database.removeMeta(session, getId());
        className = null;
        methodName = null;
        javaMethods = null;
        invalidate();
    }

    @Override
    public void checkRename() {
        throw DbException.getUnsupportedException("RENAME");
    }

    /**
     * Find the Java method that matches the arguments.
     *
     * @param args the argument list
     * @return the Java method
     * @throws DbException if no matching method could be found
     */
    public JavaMethod findJavaMethod(Expression[] args) {
        load();
        int parameterCount = args.length;
        for (JavaMethod m : javaMethods) {
            int count = m.getParameterCount();
            if (count == parameterCount || (m.isVarArgs() &&
                    count <= parameterCount + 1)) {
                return m;
            }
        }
        throw DbException.get(ErrorCode.METHOD_NOT_FOUND_1, getName() + " (" +
                className + ", parameter count: " + parameterCount + ")");
    }

    public String getJavaClassName() {
        return this.className;
    }

    public String getJavaMethodName() {
        return this.methodName;
    }

    /**
     * Get the Java methods mapped by this function.
     *
     * @return the Java methods.
     */
    public JavaMethod[] getJavaMethods() {
        load();
        return javaMethods;
    }

    public void setDeterministic(boolean deterministic) {
        this.deterministic = deterministic;
    }

    public boolean isDeterministic() {
        return deterministic;
    }

    public String getSource() {
        return source;
    }

    /**
     * There may be multiple Java methods that match a function name.
     * Each method must have a different number of parameters however.
     * This helper class represents one such method.
     */
    public static class JavaMethod implements Comparable<JavaMethod> {
        private final int id;
        private final Method method;
        private final TypeInfo dataType;
        private boolean hasConnectionParam;
        private boolean varArgs;
        private Class<?> varArgClass;
        private int paramCount;

        JavaMethod(Method method, int id) {
            this.method = method;
            this.id = id;
            Class<?>[] paramClasses = method.getParameterTypes();
            paramCount = paramClasses.length;
            if (paramCount > 0) {
                Class<?> paramClass = paramClasses[0];
                if (Connection.class.isAssignableFrom(paramClass)) {
                    hasConnectionParam = true;
                    paramCount--;
                }
            }
            if (paramCount > 0) {
                Class<?> lastArg = paramClasses[paramClasses.length - 1];
                if (lastArg.isArray() && method.isVarArgs()) {
                    varArgs = true;
                    varArgClass = lastArg.getComponentType();
                }
            }
            Class<?> returnClass = method.getReturnType();
            dataType = DataType.getTypeFromClass(returnClass);
        }

        @Override
        public String toString() {
            return method.toString();
        }

        /**
         * Check if this function requires a database connection.
         *
         * @return if the function requires a connection
         */
        public boolean hasConnectionParam() {
            return this.hasConnectionParam;
        }

        /**
         * Call the user-defined function and return the value.
         *
         * @param session the session
         * @param args the argument list
         * @param columnList true if the function should only return the column
         *            list
         * @return the value
         */
        public Value getValue(Session session, Expression[] args,
                boolean columnList) {
            Class<?>[] paramClasses = method.getParameterTypes();
            Object[] params = new Object[paramClasses.length];
            int p = 0;
            JdbcConnection conn = null;
            if (hasConnectionParam && params.length > 0) {
                params[p++] = conn = session.createConnection(columnList);
            }

            // allocate array for varArgs parameters
            Object varArg = null;
            if (varArgs) {
                int len = args.length - params.length + 1 +
                        (hasConnectionParam ? 1 : 0);
                varArg = Array.newInstance(varArgClass, len);
                params[params.length - 1] = varArg;
            }

            for (int a = 0, len = args.length; a < len; a++, p++) {
                boolean currentIsVarArg = varArgs &&
                        p >= paramClasses.length - 1;
                Class<?> paramClass;
                if (currentIsVarArg) {
                    paramClass = varArgClass;
                } else {
                    paramClass = paramClasses[p];
                }
                Value v = args[a].getValue(session);
                Object o;
                if (Value.class.isAssignableFrom(paramClass)) {
                    o = v;
                } else {
                    boolean primitive = paramClass.isPrimitive();
                    if (v == ValueNull.INSTANCE) {
                        if (primitive) {
                            if (columnList) {
                                // If the column list is requested, the parameters
                                // may be null. Need to set to default value,
                                // otherwise the function can't be called at all.
                                o = DataType.getDefaultForPrimitiveType(paramClass);
                            } else {
                                // NULL for a java primitive: return NULL
                                return ValueNull.INSTANCE;
                            }
                        } else {
                            o = null;
                        }
                    } else {
                        o = DataType.extractObjectOfType(
                                (Class<?>) (primitive ? Utils.getNonPrimitiveClass(paramClass) : paramClass),
                                v, session);
                        if (o == null) {
                            if (conn == null) {
                                conn = session.createConnection(false);
                            }
                            if (paramClass == java.sql.Array.class) {
                                o = new JdbcArray(conn, v, 0);
                            } else if (paramClass == Blob.class) {
                                o = new JdbcBlob(conn, v, JdbcLob.State.WITH_VALUE, 0);
                            } else if (paramClass == Clob.class) {
                                o = new JdbcClob(conn, v, JdbcLob.State.WITH_VALUE, 0);
                            } else if (paramClass == SQLXML.class) {
                                o = new JdbcSQLXML(conn, v, JdbcLob.State.WITH_VALUE, 0);
                            } else if (paramClass == ResultSet.class) {
                                o = new JdbcResultSet(conn, null, null, v.convertToResultSet().getResult(),
                                        0, false, true, false);
                            } else l: {
                                if (v.getValueType() == Value.JAVA_OBJECT) {
                                    o = JdbcUtils.deserialize(v.getBytes(), conn.getJavaObjectSerializer());
                                    if (paramClass.isAssignableFrom(o.getClass())) {
                                        break l;
                                    }
                                }
                                throw DbException
                                        .getUnsupportedException("converting to class " + paramClass.getName());
                            }
                        }
                    }
                }
                if (currentIsVarArg) {
                    Array.set(varArg, p - params.length + 1, o);
                } else {
                    params[p] = o;
                }
            }
            boolean old = session.getAutoCommit();
            Value identity = session.getLastScopeIdentity();
            boolean defaultConnection = session.getDatabase().
                    getSettings().defaultConnection;
            try {
                session.setAutoCommit(false);
                Object returnValue;
                try {
                    if (defaultConnection) {
                        Driver.setDefaultConnection(
                                session.createConnection(columnList));
                    }
                    returnValue = method.invoke(null, params);
                    if (returnValue == null) {
                        return ValueNull.INSTANCE;
                    }
                } catch (InvocationTargetException e) {
                    StringBuilder builder = new StringBuilder(method.getName()).append('(');
                    for (int i = 0, length = params.length; i < length; i++) {
                        if (i > 0) {
                            builder.append(", ");
                        }
                        builder.append(params[i]);
                    }
                    builder.append(')');
                    throw DbException.convertInvocation(e, builder.toString());
                } catch (Exception e) {
                    throw DbException.convert(e);
                }
                if (Value.class.isAssignableFrom(method.getReturnType())) {
                    return (Value) returnValue;
                }
                Value ret = DataType.convertToValue(session, returnValue, dataType.getValueType());
                return ret.convertTo(dataType, session);
            } finally {
                session.setLastScopeIdentity(identity);
                session.setAutoCommit(old);
                if (defaultConnection) {
                    Driver.setDefaultConnection(null);
                }
            }
        }

        public Class<?>[] getColumnClasses() {
            return method.getParameterTypes();
        }

        public TypeInfo getDataType() {
            return dataType;
        }

        public int getParameterCount() {
            return paramCount;
        }

        public boolean isVarArgs() {
            return varArgs;
        }

        @Override
        public int compareTo(JavaMethod m) {
            if (varArgs != m.varArgs) {
                return varArgs ? 1 : -1;
            }
            if (paramCount != m.paramCount) {
                return paramCount - m.paramCount;
            }
            if (hasConnectionParam != m.hasConnectionParam) {
                return hasConnectionParam ? 1 : -1;
            }
            return id - m.id;
        }

    }

}
