package com.artemis.compile;

import com.badlogic.gdx.utils.JsonValue;

import java.lang.reflect.Field;

import static com.artemis.compile.Node.node;

public class NodeFactory {
	private final CoreJsonReader jsonReader;
	private SymbolTable symbols;

	protected NodeFactory(SymbolTable symbolTable) {
		symbols = symbolTable;
		jsonReader = new CoreJsonReader();
	}

	public Node create(Class<?> type, JsonValue json) {
		Node node = node(type, json.name);

		Class<?> current = type;
		do {
			for (Field f : current.getDeclaredFields()) {
				if (SymbolTable.isValid(f)) {
					create(type, json.get(f.getName()), node);
				}
			}
			current = current.getSuperclass();
		} while (current != Object.class);

		return node;
	}

	private void create(Class<?> type, JsonValue json, Node n) {
		SymbolTable.Entry symbol = symbols.lookup(type, json.name);
		if (symbol == null) {
			String name = json != null ? json.name : null;
			throw new NullPointerException(type.getSimpleName() + "::" + name);
		}

		if (SymbolTable.isBuiltinType(symbol.type)) {
			Object o = jsonReader.read(symbol.type, json);
			n.add(node(symbol.type, symbol.field, o));
		} else {
			Node object = create(symbol.type, json);
			n.add(object);
		}
	}
}
