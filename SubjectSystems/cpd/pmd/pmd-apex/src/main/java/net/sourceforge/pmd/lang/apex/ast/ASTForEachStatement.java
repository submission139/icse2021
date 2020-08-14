/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.apex.ast;

import net.sourceforge.pmd.annotation.InternalApi;

import apex.jorje.semantic.ast.statement.ForEachStatement;

public class ASTForEachStatement extends AbstractApexNode<ForEachStatement> {

    @Deprecated
    @InternalApi
    public ASTForEachStatement(ForEachStatement forEachStatement) {
        super(forEachStatement);
    }

    @Override
    public Object jjtAccept(ApexParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }
}
