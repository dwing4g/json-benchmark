import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.DoubleToDecimal;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.NumberConverter;
import com.dslplatform.json.runtime.Settings;
import io.github.wycst.wast.common.utils.NumberUtils;
import io.github.wycst.wast.json.JSONWriter;
import jason.JsonReader;
import jason.JsonWriter;

public class NumberBenchmark {
	private static final byte[][] testBytes = {"3.1234567 ".getBytes(), "31234567.0 ".getBytes(),
			"0.31234567 ".getBytes(), "312.34567 ".getBytes(), "3.1234567e7 ".getBytes(), "3.1234567E-7 ".getBytes(),
			"0.0 ".getBytes(), "1.0 ".getBytes()};

	private static final String[] testStrs = {"3.1234567", "31234567", "0.31234567", "312.34567", "3.1234567e7",
			"3.1234567E-7", "0", "1.0"};

	private static final double[] testNums = {3.1234567, 31234567, 0.31234567, 312.34567, 3.1234567e7, 3.1234567E-7, 0,
			1.0};

	public static void testJDK() {
		var r = 0.0;
		var t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++)
				r += Double.parseDouble(testStrs[j]);
		}
		System.out.format("     JDKReader: %15.6f (%d ms)%n", r, (System.nanoTime() - t) / 1_000_000); // 624694507922444.400000

		var n = 0L;
		t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++)
				n += String.valueOf(testNums[j]).length();
		}
		System.out.format("     JDKWriter: %d (%d ms)%n", n, (System.nanoTime() - t) / 1_000_000); // 660000000
	}

	public static void testJason() {
		var jr = new JsonReader();
		var r = 0.0;
		var t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++)
				r += jr.buf(testBytes[j]).parseDouble();
		}
		System.out.format("   JasonReader: %15.6f (%d ms)%n", r, (System.nanoTime() - t) / 1_000_000); // 624694507922444.400000

		var jw = new JsonWriter();
		var n = 0L;
		t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++) {
//				n += Double.toString(tests[j]).length();
				jw.clear().write(testNums[j]);
				n += jw.size();
//				System.out.println(new String(jw.buf, 0, jw.pos));
			}
		}
		System.out.format("   JasonWriter: %d (%d ms)%n", n, (System.nanoTime() - t) / 1_000_000); // 660000000
	}

	public static void testFastJson() {
		var r = 0.0;

		var t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++) {
				try (var jr = JSONReader.of(testBytes[j])) {
					r += jr.readDoubleValue();
				}
			}
		}
		System.out.format("FastJsonReader: %15.6f (%d ms)%n", r, (System.nanoTime() - t) / 1_000_000); // 624694507922444.400000

		var buf = new byte[512];
		var n = 0L;
		t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++)
				n += DoubleToDecimal.toString(testNums[j], buf, 0, false);
		}
		System.out.format("FastJsonWriter: %d (%d ms)%n", n, (System.nanoTime() - t) / 1_000_000); // 660000000
	}

	private static final MethodHandle mhWriteDouble;

	static {
		try {
			// static int writeDouble(double doubleValue, byte[] buf, int off)
			var m = JSONWriter.class.getDeclaredMethod("writeDouble", double.class, byte[].class, int.class);
			m.setAccessible(true);
			mhWriteDouble = MethodHandles.lookup().unreflect(m);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	public static double parseDoubleWast(byte[] buf, int pos) {
		int b = buf[pos++];
		boolean neg;
		if ((neg = (b == '-')) || b == '+')
			b = buf[pos++];
		long v = 0;
		int s = 0, ev = 0, dotPos = 0;
		for (; ; b = buf[pos++]) {
			if (((b - '0') & 0x7fff_ffff) < 10) {
				if (v <= (Long.MAX_VALUE - 9) / 10)
					v = v * 10 + b - '0';
				else if (dotPos == 0)
					s--;
				else
					break;
			} else if (b == '.')
				dotPos = pos + 1;
			else if ((b | 0x20) == 'e') { // b == 'e' || b == 'E'
				if (dotPos != 0) {
					s += pos - dotPos;
					dotPos = 0;
				}
				b = buf[pos++];
				boolean expNeg;
				if ((expNeg = (b == '-')) || b == '+')
					b = buf[pos++];
				for (; ((b - '0') & 0x7fff_ffff) < 10; b = buf[pos++])
					ev = ev * 10 + b - '0';
				if (expNeg)
					ev = -ev;
				break;
			} else
				break;
		}
		if (dotPos != 0)
			s += pos - dotPos;
		double d = NumberUtils.scientificToIEEEDouble(v, s - ev);
		return neg ? -d : d;
	}

	public static void testRandomWastParser() {
		final ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < 10_000_000; i++) {
			long v;
			do
				v = r.nextLong();
			while ((v & 0x7ff0_0000_0000_0000L) == 0x7ff0_0000_0000_0000L);
			final double f = Double.longBitsToDouble(v);
			final double f2 = parseDoubleWast((f + " ").getBytes(StandardCharsets.ISO_8859_1), 0);
			if (f != f2 && !(Double.isNaN(f) && Double.isNaN(f2)))
				throw new AssertionError("testRandomWastParser[" + i + "]: " + f + " != " + f2);
		}
		System.out.println("testRandomWastParser OK!");
	}

	public static void testWast() throws Throwable {
		var r = 0.0;
		var t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++)
				r += parseDoubleWast(testBytes[j], 0);
		}
		System.out.format("    WastReader: %15.6f (%d ms)%n", r, (System.nanoTime() - t) / 1_000_000); // 624694507922444.400000

		var buf = new byte[32];
		var n = 0L;
		t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++) {
//				n += Double.toString(tests[j]).length();
				n += (int)mhWriteDouble.invoke(testNums[j], buf, 0);
//				System.out.println(new String(jw.buf, 0, jw.pos));
			}
		}
		System.out.format("    WastWriter: %d (%d ms)%n", n, (System.nanoTime() - t) / 1_000_000); // 680000000
	}

	private static final Object numberParser;
	private static final MethodHandle mhParseDouble;

	static {
		try {
			var c = Class.forName("org.simdjson.NumberParser").getDeclaredConstructor();
			c.setAccessible(true);
			numberParser = c.newInstance();
			// double parseDouble(byte[] buffer, int len, int offset)
			var m = numberParser.getClass().getDeclaredMethod("parseDouble", byte[].class, int.class, int.class);
			m.setAccessible(true);
			mhParseDouble = MethodHandles.lookup().unreflect(m);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	public static void testRandomSimdJsonParser() throws Throwable {
		final ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < 10_000_000; i++) {
			long v;
			do
				v = r.nextLong();
			while ((v & 0x7ff0_0000_0000_0000L) == 0x7ff0_0000_0000_0000L);
			final double f = Double.longBitsToDouble(v);
			final byte[] buf = (f + " ").getBytes(StandardCharsets.ISO_8859_1);
			final double f2 = (double)mhParseDouble.invoke(numberParser, buf, buf.length, 0);
			if (f != f2 && !(Double.isNaN(f) && Double.isNaN(f2)))
				throw new AssertionError("testRandomSimdJsonParser[" + i + "]: " + f + " != " + f2);
		}
		System.out.println("testRandomSimdJsonParser OK!");
	}

	public static void testSimdJson() throws Throwable {
		var r = 0.0;
		var t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++)
				r += (double)mhParseDouble.invoke(numberParser, testBytes[j], testBytes[j].length - 1, 0);
		}
		System.out.format("SimdJsonReader: %15.6f (%d ms)%n", r, (System.nanoTime() - t) / 1_000_000); // 624694507922444.400000
	}

	public static void testDslJson() throws IOException {
		var dj = new DslJson<>(Settings.withRuntime().includeServiceLoader());
		var r = 0.0;
		var jr = dj.newReader();
		var t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++) {
				jr.process(testBytes[j], testBytes[j].length - 1).read();
				r += NumberConverter.deserializeDouble(jr);
			}
		}
		System.out.format(" DslJsonReader: %15.6f (%d ms)%n", r, (System.nanoTime() - t) / 1_000_000); // 624694507922444.400000

		var jw = dj.newWriter(new byte[512]);
		var n = 0L;
		t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++) {
				jw.reset();
				NumberConverter.serialize(testNums[j], jw);
				n += jw.size();
			}
		}
		System.out.format(" DslJsonWriter: %d (%d ms)%n", n, (System.nanoTime() - t) / 1_000_000); // 660000000
	}

	@SuppressWarnings("unchecked")
	public static void main1(String[] args) {
//		double d = -7.936565429908449E-131;
//		String s = Double.toString(d);
//		System.out.println(s); // 输出: 9.49113649602955E-309
//		double d1 = ((List<Double>)io.github.wycst.wast.json.JSON.parse("[" + s + "]")).getFirst();
//		System.out.println(d1); // 输出: 9.491136496029548E-309
//		double d2 = Double.parseDouble(s);
//		System.out.println(d2); // 输出: 9.49113649602955E-309
//		System.out.println(d1 == d2); // 输出: false

		final ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < 10000000; i++) {
			long v;
			do
				v = r.nextLong();
			while ((v & 0x7ff0000000000000L) == 0x7ff0000000000000L); // 排除Infinity,NaN
			double d = Double.longBitsToDouble(v);
			double d1 = ((List<? extends Number>)io.github.wycst.wast.json.JSON.parse("[" + d + "]")).get(0).doubleValue();
			if (d != d1)
				throw new AssertionError("testRandomWastParser[" + i + "]: " + d + " != " + d1);
		}
		System.out.println("testRandomWastParser OK!");
	}

	public static void main(String[] args) throws Throwable {
		testRandomWastParser();
		// testRandomSimdJsonParser();

		for (int i = 0; i < 5; i++) {
			testJDK();
			testJason();
			testFastJson();
			testWast();
			testSimdJson();
			testDslJson();
		}
	}
}
