import java.io.IOException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.util.DoubleToDecimal;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.NumberConverter;
import com.dslplatform.json.runtime.Settings;
import jason.JsonReader;
import jason.JsonWriter;

public class NumberBenchmark {
	private static final byte[][] testBytes = {"3.1234567 ".getBytes(), "31234567 ".getBytes(),
			"0.31234567 ".getBytes(), "312.34567 ".getBytes(), "3.1234567e7 ".getBytes(), "3.1234567E-7 ".getBytes(),
			"0 ".getBytes(), "1.0 ".getBytes()};

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

	public static void testDslJson() throws IOException {
		var dj = new DslJson<>(Settings.withRuntime().includeServiceLoader());
		var r = 0.0;
		var jr = dj.newReader();
		var t = System.nanoTime();
		for (int i = 0; i < 10_000_000; i++) {
			for (int j = 0; j < 8; j++) {
				jr.process(testBytes[j], testBytes[j].length).read();
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

	public static void main(String[] args) throws IOException {
		testJDK();
		testFastJson();
		testDslJson();
		testJason();
	}
}
