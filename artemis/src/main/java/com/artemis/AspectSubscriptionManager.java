package com.artemis;

import com.artemis.annotations.SkipWire;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import static com.artemis.utils.ConverterUtil.toIntBag;

@SkipWire
public class AspectSubscriptionManager extends Manager {

	private final Map<Aspect.Builder, EntitySubscription> subscriptionMap;
	private Bag<EntitySubscription> subscriptions;

	private final IntBag addedIds = new IntBag();
	private final IntBag changedIds = new IntBag();
	private final IntBag deletedIds = new IntBag();

	protected AspectSubscriptionManager() {
		subscriptionMap = new HashMap<Aspect.Builder, EntitySubscription>();
		subscriptions = new Bag<EntitySubscription>();
	}

	public EntitySubscription get(Aspect.Builder builder) {
		EntitySubscription subscription = subscriptionMap.get(builder);
		return (subscription != null) ? subscription : createSubscription(builder);
	}

	private EntitySubscription createSubscription(Aspect.Builder builder) {
		EntitySubscription entitySubscription = new EntitySubscription(world, builder);
		subscriptionMap.put(builder, entitySubscription);
		subscriptions.add(entitySubscription);

		world.getEntityManager().synchronize(entitySubscription);

		return entitySubscription;
	}

	/**
	 * Informs all listeners of added, changed and deleted changes.
	 *
	 * Two types of listeners:
	 * {@see EntityObserver} implementations are guaranteed to be called back in order of system registration.
	 * {@see com.artemis.EntitySubscription.SubscriptionListener}, where order can vary (typically ordinal, except
	 * for subscriptions created in process, initialize instead of setWorld).
     *
	 * {@link com.artemis.EntitySubscription.SubscriptionListener#inserted(IntBag)}
	 * {@link com.artemis.EntitySubscription.SubscriptionListener#removed(IntBag)}
	 *
	 * Observers are called before Subscriptions, which means managerial tasks get artificial priority.
	 *
	 * @param added Entities added to world
	 * @param changed Entities with changed composition (not state).
	 * @param deleted Entities removed from world.
	 */
	void process(BitSet added, BitSet changed, BitSet deleted) {
		toEntityIntBags(added, changed, deleted);

		Object[] subscribers = subscriptions.getData();
		((EntitySubscription)subscribers[0]).processAll(addedIds, changedIds, deletedIds);

		for (int i = 1, s = subscriptions.size(); s > i; i++) {
			EntitySubscription subscriber = (EntitySubscription)subscribers[i];
			subscriber.process(addedIds, changedIds, deletedIds);
		}


		addedIds.setSize(0);
		changedIds.setSize(0);
		deletedIds.setSize(0);
	}

	private void toEntityIntBags(BitSet added, BitSet changed, BitSet deleted) {
		toIntBag(added, addedIds);
		toIntBag(changed, changedIds);
		toIntBag(deleted, deletedIds);

		added.clear();
		changed.clear();
		deleted.clear();
	}

	void processComponentIdentity(int id, BitSet componentBits) {
		Object[] subscribers = subscriptions.getData();
		for (int i = 0, s = subscriptions.size(); s > i; i++) {
			EntitySubscription subscriber = (EntitySubscription)subscribers[i];
			subscriber.processComponentIdentity(id, componentBits);
		}
	}
}
