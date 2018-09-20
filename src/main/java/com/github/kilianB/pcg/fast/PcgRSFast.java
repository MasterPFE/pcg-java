package com.github.kilianB.pcg.fast;

import java.lang.reflect.InvocationTargetException;
import java.util.Random;

import com.github.kilianB.pcg.Pcg;

/**
 * A 64 bit State PcgRNG with 32 bit output. PCG-XSH-RR <p>
 * 
 * The pcg family combines a linear congruential generators with a permutation
 * output function resulting in high quality pseudo random numbers. <p>
 * 
 * The original concept was introduced by Melissa O’Neill please refer to <a
 * href="http://www.pcg-random.org/">pcg-random</a> for more information. <p>
 * Opposed to RR this version performs a random shift rather than a random
 * rotation.
 * 
 * The RS instance permutates the output using the following function:
 * 
 * <pre>
 * {@code
 * ((state >>> 22) ^ state) >>> ((state >>> 61) + 22)
 * }
 * </pre>
 * 
 * This implementation is <b>Not</b> thread safe, inlines most methods manually
 * and performs other optimizations to maximize the throughput.
 * 
 * While the basic methods to retrieve datatypes are implemented no guarantee is made
 * regarding the stream and splitterator methods provided by the Random class. Additional 
 * tests have to be performed if they impact the internal state of this class in a harmful way.
 * 
 * @author Kilian
 * @see <a href="http://www.pcg-random.org/">www.pcg-random.org</a>
 */
public class PcgRSFast extends Random implements Pcg {

	private static final long serialVersionUID = -4257915988930727506L;

	
	/**
	 * Linear congruential constant. Same as MMIX by Donald Knuth and Newlib, Musl
	 */
	private static final long MULT_64 = 6364136223846793005L;

	// static final variables are inlined by default
	private static final double DOUBLE_MASK = 1L << 53;
	private static final float FLOAT_UNIT = (float) (1 << 24);
	private static final long INTEGER_MASK = 0xFFFFFFFFL;

	// 64 version
	/** 64 bit internal state */
	protected long state;
	/** Stream number of the rng. */
	protected long inc;

	private boolean gausAvailable;
	private double nextGaus;

	// private static final int INTEGER_MASK_SIGNED = 0xFFFFFFFF;

	/**
	 * Create a PcgRSFast instance seeded with with 2 longs generated by xorshift*. 
	 * The values chosen are very likely not used as seeds in any other non argument constructor
	 * of any of the classes provided in this library. 
	 */
	public PcgRSFast() {
		this(getRandomSeed(), getRandomSeed());
	}

	public PcgRSFast(long seed, long streamNumber) {
		state = 0;
		inc = (streamNumber << 1) | 1; // 2* + 1
		state = (state * MULT_64) + inc;
		state += seed;
		// Due to access to inlined vars the fast implementation is one step ahead of
		// the ordinary rngs. To get same results we can skip the state update
		
		// state = (state * MULT_64) + inc;
	}

	protected PcgRSFast(long initialState, long increment, boolean dummy) {
		setState(initialState);
		setInc(increment);
	}

	/**
	 * Advance or set back the rngs state.
	 * 
	 * In other words fast skip the next n generated random numbers or set the PNG
	 * back so it will create the last n numbers in the same sequence again.
	 * 
	 * <pre>
	 * 	int x = nextInt();
	 * 	nextInt(); nextInt();
	 * 	step(-3);
	 *	int y = nextInt(); 
	 *	x == y TRUE
	 * </pre>
	 * 
	 * Be aware that this relationship is only true for deterministic generation
	 * calls. {@link #nextGaussian()} or any bound limited number generations might
	 * loop and consume more than one step to generate a number. <p>
	 * 
	 * To advance n steps the function performs <code>Math.ceil( log2(n) )</code>
	 * iterations. So you may go ahead and skip as many steps as you like without
	 * any performance implications. <p>
	 * 
	 * Negative indices can be used to jump backwards in time going the long way
	 * around
	 * 
	 * 
	 * @param steps
	 *            the amount of steps to advance or in case of a negative number go
	 *            back in history
	 * 
	 */
	public void advance(long steps) {
		long acc_mult = 1;
		long acc_plus = 0;

		long cur_plus = inc;
		long cur_mult = MULT_64;

		while (Long.compareUnsigned(steps, 0) > 0) {
			if ((steps & 1) == 1) { 	// Last significant bit is 1
				acc_mult *= cur_mult;
				acc_plus = acc_plus * cur_mult + cur_plus;
			}
			cur_plus *= (cur_mult + 1);
			cur_mult *= cur_mult;
			steps = Long.divideUnsigned(steps, 2);
		}
		state = (acc_mult * state) + acc_plus;
	}

	public byte nextByte() {
		state = (state * MULT_64) + inc;
		return (byte) ((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) >>> 24);
	}

	public void nextBytes(byte[] b) {
		for (int i = 0; i < b.length; i++) {
			state = (state * MULT_64) + inc;
			b[i] = (byte) ((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) >>> 24);
		}
	}

	public char nextChar() {
		state = (state * MULT_64) + inc;
		// Why should we cast it to an int first can't we mask it to a char directly?
		return (char) ((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) >>> 16);
	}

	public short nextShort() {
		state = (state * MULT_64) + inc;
		return (short) ((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) >>> 16);
	}

	/**
	 * Returns the next pseudorandom, uniformly distributed {@code int} value from
	 * this random number generator's sequence. The general contract of
	 * {@code nextInt} is that one {@code int} value is pseudorandomly generated and
	 * returned. All 2<sup>32</sup> possible {@code int} values are produced with
	 * (approximately) equal probability.
	 * 
	 * @return the next pseudorandom, uniformly distributed {@code int} value from
	 *         this random number generator's sequence
	 */
	public int nextInt() {
		// we miss a single state and keep an old value around. but this does not alter
		// The produced number but shifts them 1 back.
		state = (state * MULT_64) + inc;
		// long oldState = state;
		return (int) (((state >>> 22) ^ state) >>> ((state >>> 61) + 22));
	}

	/**
	 * Returns a pseudorandom, uniformly distributed {@code int} value between 0
	 * (inclusive) and the specified value (exclusive), drawn from this random
	 * number generator's sequence.
	 * 
	 * @param n
	 *            the upper bound (exclusive). Must be positive.
	 * @return the next pseudorandom, uniformly distributed {@code int} value
	 *         between zero (inclusive) and {@code bound} (exclusive) from this
	 *         random number generator's sequence
	 */
	public int nextInt(int n) {
		state = (state * MULT_64) + inc;
		int r = (int) (((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) >>> 1;	// Unsigned!
		int m = n - 1;
		if ((n & m) == 0)  // i.e., bound is a power of 2
			r = (int) ((n * (long) r) >> 31);
		else {
			for (int u = r; u - (r = u % n) + m < 0;) {
				state = (state * MULT_64) + inc;
				u = (int) (((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) >>> 1;
			}
		}
		return r;
	};

	/**
	 * Returns the next pseudorandom, uniformly distributed {@code boolean} value
	 * from this random number generator's sequence. The general contract of
	 * {@code nextBoolean} is that one {@code boolean} value is pseudorandomly
	 * generated and returned. The values {@code true} and {@code false} are
	 * produced with (approximately) equal probability.
	 * 
	 * @return the next pseudorandom, uniformly distributed {@code boolean} value
	 *         from this random number generator's sequence
	 */
	public boolean nextBoolean() {
		// Two choices either take the low bit or get a range 2 int and make an if
		state = (state * MULT_64) + inc;
		return (((((state >>> 22) ^ state) >>> (state >>> 61) + 22) & INTEGER_MASK) >>> 31) != 0;
	}

	public boolean nextBoolean(double probability) {
		// Borrowed from https://cs.gmu.edu/~sean/research/mersenne/MersenneTwister.java
		if (probability == 0.0)
			return false;
		if (probability == 1.0)
			return true;

		state = (state * MULT_64) + inc;
		long l = ((((state >>> 22) ^ state) >>> ((state >>> 61) + 22))) & INTEGER_MASK;

		state = (state * MULT_64) + inc;

		return (((l >>> 6) << 27) + (((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) & INTEGER_MASK) >>> 5))
				/ DOUBLE_MASK < probability;
	}

	public long nextLong() {

		state = (state * MULT_64) + inc;
		// No need to mask if we shift by 32 bits
		long l = (((state >>> 22) ^ state) >>> ((state >>> 61) + 22));

		state = (state * MULT_64) + inc;
		long j = (((state >>> 22) ^ state) >>> ((state >>> 61) + 22));

		// Long keep consistent with the random definition of keeping the lower word
		// signed,
		// But should this really be the case? Why don't we mask the sign bit?
		return (l << 32) + (int) j;
	}

	public long nextLong(long n) {
		long bits, val;
		do {
			state = (state * MULT_64) + inc;
			// No need to mask if we shift by 32 bits
			long l = (((state >>> 22) ^ state) >>> ((state >>> 61) + 22));

			state = (state * MULT_64) + inc;
			long j = (((state >>> 22) ^ state) >>> ((state >>> 61) + 22));

			bits = ((l << 32) + (int) j >>> 1);
			val = bits % n;
		} while (bits - val + (n - 1) < 0);
		return val;
	}

	public double nextDouble() {
		state = (state * MULT_64) + inc;
		long l = ((((state >>> 22) ^ state) >>> ((state >>> 61) + 22))) & INTEGER_MASK;
		state = (state * MULT_64) + inc;
		return (((l >>> 6) << 27) + (((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) & INTEGER_MASK) >>> 5))
				/ DOUBLE_MASK;
	}

	public double nextDouble(boolean includeZero, boolean includeOne) {
		double d = 0.0;
		do {
			state = (state * MULT_64) + inc;
			long l = ((((state >>> 22) ^ state) >>> ((state >>> 61) + 22))) & INTEGER_MASK;
			state = (state * MULT_64) + inc;
			d = (((l >>> 6) << 27) + (((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) & INTEGER_MASK) >>> 5))
					/ DOUBLE_MASK;

			// grab a value, initially from half-open [0.0, 1.0)
			if (includeOne) {
				// Only generate the boolean if it really is the case or we scramble the state
				state = (state * MULT_64) + inc;
				if ((((((state >>> 22) ^ state) >>> (state >>> 61) + 22) & INTEGER_MASK) >>> 31) != 0) {
					d += 1.0;
				}

			}

		} while ((d > 1.0) ||                            // everything above 1.0 is always invalid
				(!includeZero && d == 0.0));            // if we're not including zero, 0.0 is invalid
		return d;
	}

	public float nextFloat() {
		state = (state * MULT_64) + inc;
		return (((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) & INTEGER_MASK) >>> 8) / FLOAT_UNIT;

	}

	public float nextFloat(boolean includeZero, boolean includeOne) {
		float d = 0.0f;
		do {
			state = (state * MULT_64) + inc;
			d = (((((state >>> 22) ^ state) >>> ((state >>> 61) + 22)) & INTEGER_MASK) >>> 8) / FLOAT_UNIT; // grab a
																											 // value,
																											 // initially
																											 // from
																											 // half-open
																											 // [0.0f,
																											 // 1.0f)
			if (includeOne) {
				// Only generate the boolean if it really is the case or we scramble the state
				state = (state * MULT_64) + inc;
				if ((((((state >>> 22) ^ state) >>> (state >>> 61) + 22) & INTEGER_MASK) >>> 31) != 0) {
					d += 1.0f;
				}
			}
		} while ((d > 1.0f) || // everything above 1.0f is always invalid
				(!includeZero && d == 0.0f)); // if we're not including zero, 0.0f is invalid
		return d;
	}

	public double nextGaussian() {
		// Borrowed from https://cs.gmu.edu/~sean/research/mersenne/MersenneTwister.java

		// Shall we go atomic? the issue is after setting and returning a 2nd thread
		// could create
		// a new gaus making the following call return the same value. But for now we
		// don't care
		// about thread safety anyways
		if (gausAvailable) {
			gausAvailable = false;
			return nextGaus;
		} else {
			double v1, v2, s;
			do {
				v1 = 2 * nextDouble() - 1; // between -1.0 and 1.0
				v2 = 2 * nextDouble() - 1; // between -1.0 and 1.0
				s = v1 * v1 + v2 * v2;
			} while (s >= 1 || s == 0);
			double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
			nextGaus = v2 * multiplier;
			gausAvailable = true;
			return v1 * multiplier;
		}
	}

	@Override
	public long getInc() {
		return inc;
	}

	@Override
	public long getState() {
		return state;
	}

	protected void setInc(long increment) {
		if (increment == 0 || increment % 2 == 0) {
			throw new IllegalArgumentException("Increment may not be 0 or even. Value: " + increment);
		}
		this.inc = increment;
	}

	protected void setState(long state) {
		this.state = state;
	}

	// No reason to inline the methods below. They won't be called nearly as often
	// to justify duplicate code

	@Override
	@SuppressWarnings("unchecked")
	public <T> T split() throws ReflectiveOperationException {
		try {
			return (T) getClass().getDeclaredConstructor(long.class, long.class, boolean.class).newInstance(getState(),
					getInc(), true);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			e.getCause().printStackTrace();
			throw new ReflectiveOperationException("Failed to instantiate clone constructor");
		}
	}

	@Override
	public <T> T splitDistinct() throws ReflectiveOperationException {
		try {
			long curInc, curState;

			// No reason to CAS here. we don't swap the inc around all the time
			do {
				// Has to be odd
				curInc = ((nextLong(Math.abs(getInc())) ^ (~System.nanoTime())) * 2) + 1;
			} while (curInc == getInc());

			// State swaps by each call to nextLong
			do {
				curState = (nextLong(Math.abs(getState())) ^ (~System.nanoTime()));
			} while (curState == getState());

			return (T) getClass().getDeclaredConstructor(long.class, long.class, boolean.class).newInstance(curState,
					curInc, true);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			e.getCause().printStackTrace();
			throw new ReflectiveOperationException("Failed to instantiate clone constructor");
		}
	}

	@Override
	public int next(int n) {
		throw new UnsupportedOperationException("Fast methods don't implement next method");
	}

	@Override
	public long getMult() {
		return MULT_64;
	}

	@Override
	public boolean isFast() {
		return true;
	}

	protected static long getRandomSeed() {
		// xorshift64*
		for (;;) {
			long current = UNIQUE_SEED.get();
			long next = current;
			next ^= next >> 12;
			next ^= next << 25; // b
			next ^= next >> 27; // c
			next *= 0x2545F4914F6CDD1DL;
			if (UNIQUE_SEED.compareAndSet(current, next))
				return next;
		}
	}
}
