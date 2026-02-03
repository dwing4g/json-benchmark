import com.alibaba.fastjson2.JSON;
import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.jsoniter.JsonIterator;
import com.jsoniter.spi.DecodingMode;
import jason.JsonReader;
import org.simdjson.SimdJsonParser;

// --add-opens java.base/java.lang=ALL-UNNAMED -Xms1g -Xmx1g -Xmn256m
public class JsonBenchmark {
	@CompiledJson
	public static class C { // 测试的类
		public int a;
		@SuppressWarnings("unused")
		public String b;
		public C c;
	}

	// 测试的JSON字符串
	static String str = "{\"b\":\"xyz\", \"a\":123,\"c\":{\"a\":456,\"b\":\"abc\"}}";
	static byte[] bytes = str.getBytes();
	static final int TEST_COUNT = 10_000_000;

	static void testJason() throws Exception { // jason.jar大小51.5KB
		long t = System.nanoTime(), v = 0;
		JsonReader jr = new JsonReader();
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = jr.buf(bytes).parse(C.class);
			//noinspection ConstantConditions
			v += c.a + c.c.a;
		}
		System.out.println("   Jason: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testFastJson() { // fastjson2-2.0.60.jar大小2.06MB
		long t = System.nanoTime(), v = 0;
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = JSON.parseObject(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println("FastJson: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testWast() { // wast-0.0.29.jar大小1.31MB
		long t = System.nanoTime(), v = 0;
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = io.github.wycst.wast.json.JSON.parseObject(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println("    Wast: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	@SuppressWarnings("unused")
	static void testSimdJson() { // simdjson-java-0.4.0.jar大小96KB
		long t = System.nanoTime(), v = 0;
		SimdJsonParser parser = new SimdJsonParser();
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = parser.parse(bytes, bytes.length, C.class);
			v += c.a + c.c.a;
		}
		System.out.println("SimdJson: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testJsoniter() { // jsoniter-0.9.23.jar和javassist-3.30.2-GA.jar总大小1.09MB
		long t = System.nanoTime(), v = 0;
		// 设置最快的模式,需要借助javassist库(javassist-3.29.2-GA.jar大小776KB)
		JsonIterator.setMode(DecodingMode.DYNAMIC_MODE_AND_MATCH_FIELD_WITH_HASH);
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = JsonIterator.deserialize(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println("Jsoniter: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testDslJson() throws Exception { // dsl-json-2.0.2.jar大小446KB
		long t = System.nanoTime(), v = 0;
		DslJson<Object> dj = new DslJson<>(Settings.withRuntime().includeServiceLoader());
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = dj.deserialize(C.class, bytes, bytes.length);
			//noinspection ConstantConditions
			v += c.a + c.c.a;
		}
		System.out.println("Dsl-Json: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testJackson() throws Exception { // jackson-*-2.21.jar四个文件总大小2.47MB
		long t = System.nanoTime(), v = 0;
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new AfterburnerModule()); // 实测这里Afterburner插件效果不理想,只提速15%
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = om.readValue(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println(" Jackson: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	@SuppressWarnings({"UnnecessaryModifier", "unused"})
	public static void main(String[] args) throws Exception {
		// 各测试5轮,排除预热影响,以最少时间为准
		for (int i = 0; i < 5; ++i)
			testJason();
		System.gc();
		Thread.sleep(1000);
		for (int i = 0; i < 5; ++i)
			testFastJson();
		System.gc();
		Thread.sleep(1000);
		for (int i = 0; i < 5; ++i)
			testWast();
		System.gc();
		Thread.sleep(1000);
//		for (int i = 0; i < 5; ++i)
//			testSimdJson();
//		System.gc();
//		Thread.sleep(1000);
		for (int i = 0; i < 5; ++i)
			testJsoniter();
		System.gc();
		Thread.sleep(1000);
		for (int i = 0; i < 5; ++i)
			testDslJson();
		System.gc();
		Thread.sleep(1000);
		for (int i = 0; i < 5; ++i)
			testJackson();
	}
}
// OpenJDK 25
//    Jason: 5790000000, 822ms
// FastJson: 5790000000, 1077ms
//     Wast: 5790000000, 996ms
// Jsoniter: 5790000000, 1054ms
// Dsl-Json: 5790000000, 2458ms
//  Jackson: 5790000000, 3973ms
