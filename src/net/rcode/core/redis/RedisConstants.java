package net.rcode.core.redis;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RedisConstants {
	public static <T> Set<T> createSet(T... members) {
		HashSet<T> set=new HashSet<T>(members.length*2);
		for (T member: members) {
			set.add(member);
		}
		return Collections.unmodifiableSet(set);
	}
	
	/**
	 * Set of upper case command names that change the state of a connection
	 */
	public static final Set<String> STATE_COMMANDS=createSet(
			"AUTH",
			"SELECT"
			);
	
	public static final Set<String> SUBSCRIBE_COMMANDS=createSet(
			"PSUBSCRIBE",
			"PUNSUBSCRIBE",
			"SUBSCRIBE",
			"UNSUBSCRIBE"
			);
	
	public static final Set<String> SUBSCRIPTION_MESSAGE_TYPES=createSet(
			"subscribe",
			"unsubscribe",
			"psubscribe",
			"punsubscribe"
			);
}
