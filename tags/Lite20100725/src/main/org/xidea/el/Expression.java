package org.xidea.el;

import java.util.Map;

public interface Expression{
	/**
	 * 根据传入的变量上下文，执行表达式
	 * @see ExpressionImpl#evaluate(Map)
	 * @param context 变量表
	 * @return
	 */
	public Object evaluate(Object context);
}