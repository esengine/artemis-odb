package com.artemis.io;

import com.artemis.*;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;
import com.artemis.utils.reflect.ClassReflection;
import com.artemis.utils.reflect.Constructor;
import com.artemis.utils.reflect.ReflectionException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public class JsonArtemisSerializer extends WorldSerializationManager.ArtemisSerializer<Json.Serializer> {
	private final Json json;
	private final ComponentLookupSerializer lookup;
	private final IntBagEntitySerializer intBagEntitySerializer;
	private final TransmuterEntrySerializer transmuterEntrySerializer;
	private final EntitySerializer entitySerializer;
	private final ComponentCollector componentCollector;

	private boolean prettyPrint;
	private ReferenceTracker referenceTracker;

	public JsonArtemisSerializer(World world) {
		super(world);

		componentCollector = new ComponentCollector(world);
		referenceTracker = new ReferenceTracker(world);

		lookup = new ComponentLookupSerializer();
		intBagEntitySerializer = new IntBagEntitySerializer(world);
		entitySerializer = new EntitySerializer(world, referenceTracker);
		transmuterEntrySerializer = new TransmuterEntrySerializer();

		json = new Json(JsonWriter.OutputType.json);
		json.setIgnoreUnknownFields(true);
		json.setSerializer(SaveFileFormat.ComponentIdentifiers.class, lookup);
		json.setSerializer(Bag.class, new EntityBagSerializer(world));
		json.setSerializer(IntBag.class, intBagEntitySerializer);
		json.setSerializer(Entity.class, entitySerializer);
		json.setSerializer(ArchetypeMapper.class, new ArchetypeMapperSerializer());
		json.setSerializer(ArchetypeMapper.TransmuterEntry.class, transmuterEntrySerializer);
	}

	public JsonArtemisSerializer prettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
		return this;
	}

	public JsonArtemisSerializer setUsePrototypes(boolean usePrototypes) {
		json.setUsePrototypes(usePrototypes);
		entitySerializer.setUsePrototypes(usePrototypes);
		return this;
	}

	@Override
	public WorldSerializationManager.ArtemisSerializer register(Class<?> type, Json.Serializer serializer) {
		json.setSerializer(type, serializer);
		return this;
	}

	@Override
	protected void save(Writer writer, SaveFileFormat save) {
		try {
			save.archetypes = new ArchetypeMapper(world, save.entities);

			referenceTracker.inspectTypes(world);
			referenceTracker.preWrite(save);

			componentCollector.preWrite(save);
			entitySerializer.serializationState = save;
			transmuterEntrySerializer.identifiers = save.componentIdentifiers;
			entitySerializer.archetypeMapper = new ArchetypeMapper(world, save.entities);
			entitySerializer.archetypeMapper.serializationState = save;
			save.componentIdentifiers.build();
			if (prettyPrint) {
				writer.append(json.prettyPrint(save));
			} else {
				json.toJson(save, writer);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected <T extends SaveFileFormat> T load(InputStream is, Class<T> format) {
		JsonValue jsonData = new JsonReader().parse(is);

		entitySerializer.preLoad();

		SaveFileFormat partial = partialLoad(jsonData);
		referenceTracker.inspectTypes(partial.componentIdentifiers.nameToType.values());

		T t = newInstance(format);
		json.readFields(t, jsonData);
		t.tracker = entitySerializer.keyTracker;
		referenceTracker.translate(intBagEntitySerializer.getTranslatedIds());
		return t;
	}

	private <T extends SaveFileFormat> T newInstance(Class<T> format) {
		try {
			Constructor ctor = ClassReflection.getDeclaredConstructor(format);
			ctor.setAccessible(true);
			return (T) ctor.newInstance();
		} catch (ReflectionException e) {
			throw new RuntimeException(e);
		}
	}

	private SaveFileFormat partialLoad(JsonValue jsonMap) {
		SaveFileFormat save = new SaveFileFormat((IntBag)null);
		json.readField(save, "componentIdentifiers", jsonMap);
		transmuterEntrySerializer.identifiers = save.componentIdentifiers;

		json.readField(save, "archetypes", jsonMap);
		entitySerializer.archetypeMapper = save.archetypes;

		entitySerializer.serializationState = save;
		if (entitySerializer.archetypeMapper != null) {
			entitySerializer.archetypeMapper.serializationState = save;
			transmuterEntrySerializer.identifiers = save.componentIdentifiers;
		}

		return save;
	}
}
