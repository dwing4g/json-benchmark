import com.alibaba.fastjson2.JSON;
import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.jsoniter.JsonIterator;
import com.jsoniter.spi.DecodingMode;
import jason.JsonReader;

// --add-opens java.base/java.lang=ALL-UNNAMED
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

	static void testJackson() throws Exception { // jackson-*-2.16.0.jar四个文件总大小2.39MB
		long t = System.nanoTime(), v = 0;
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new AfterburnerModule()); // 实测这里Afterburner插件效果不理想,只提速15%
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = om.readValue(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println(" Jackson: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testFastJson() { // fastjson2-2.0.43.jar大小1.9MB
		long t = System.nanoTime(), v = 0;
		for (int i = 0; i < TEST_COUNT; ++i) {
			// C c = JSON.parseObject(bytes, C.class);
			C c = JSON.parseObject(str, C.class); // 只有FastJson使用String输入比byte[]性能更高
			v += c.a + c.c.a;
		}
		System.out.println("FastJson: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testDslJson() throws Exception { // dsl-json-2.0.2.jar大小447KB
		long t = System.nanoTime(), v = 0;
		DslJson<Object> dj = new DslJson<>(Settings.withRuntime().includeServiceLoader());
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = dj.deserialize(C.class, bytes, bytes.length);
			//noinspection ConstantConditions
			v += c.a + c.c.a;
		}
		System.out.println("Dsl-Json: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testJsoniter() { // jsoniter-0.9.23.jar和javassist-3.29.2-GA.jar总大小1.09MB
		long t = System.nanoTime(), v = 0;
		// 设置最快的模式,需要借助javassist库(javassist-3.29.2-GA.jar大小776KB)
		JsonIterator.setMode(DecodingMode.DYNAMIC_MODE_AND_MATCH_FIELD_WITH_HASH);
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = JsonIterator.deserialize(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println("Jsoniter: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testJason() throws Exception { // jason.jar大小49KB
		long t = System.nanoTime(), v = 0;
		JsonReader jr = new JsonReader();
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = jr.buf(bytes).parse(C.class);
			//noinspection ConstantConditions
			v += c.a + c.c.a;
		}
		System.out.println("   Jason: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	public static void main(String[] args) throws Exception {
		// 各测试5轮,排除预热影响,以最少时间为准
		for (int i = 0; i < 5; ++i)
			testJackson();
		System.gc();
		for (int i = 0; i < 5; ++i)
			testFastJson();
		System.gc();
		for (int i = 0; i < 5; ++i)
			testDslJson();
		System.gc();
		for (int i = 0; i < 5; ++i)
			testJsoniter();
		System.gc();
		for (int i = 0; i < 5; ++i)
			testJason();
	}
}
// OpenJDK 21.0.1
//  Jackson: 5790000000, 4073ms
// FastJson: 5790000000, 1154ms
// Dsl-Json: 5790000000, 2446ms
// Jsoniter: 5790000000, 1167ms
//    Jason: 5790000000, 973ms
