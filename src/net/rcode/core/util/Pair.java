package net.rcode.core.util;

/**
 * Pair class for old sdks that don't support it
 * 
 * @author stella
 *
 * @param <F>
 * @param <S>
 */
public class Pair<F,S> {
	public final F first;
	public final S second;
	
	public Pair(F first, S second) {
		this.first=first;
		this.second=second;
	}
	
	public static <A,B> Pair<A,B> create(A a, B b) {
		return new Pair<A,B>(a,b);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o==null) return false;
		try {
			Pair<F,S> other=(Pair<F, S>) o;
			F ofirst=other.first;
			S osecond=other.second;
			return (ofirst==null ? first==null : ofirst.equals(first)) &&
					(osecond==null ? second==null : osecond.equals(second));
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		int hash=1;
		if (first!=null) hash=31*hash + first.hashCode();
		if (second!=null) hash=31*hash + second.hashCode();
		return hash;
	}
}
