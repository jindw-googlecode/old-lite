package org.xidea.lite.tools.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jside.webserver.action.URLMatcher;
import org.w3c.dom.Document;
import org.xidea.lite.impl.ParseUtil;
import org.xidea.lite.parse.ParseConfig;
import org.xidea.lite.parse.ParseContext;
import org.xidea.lite.tools.FilterPlugin;
import org.xidea.lite.tools.ResourceManager;
import org.xml.sax.SAXException;

public class ResourceManagerImpl implements ResourceManager {
	private static Log log = LogFactory.getLog(ResourceManagerImpl.class);
	private ParseConfig config;
	private File root;
	private ArrayList<MatcherFilter<byte[]>> streamFilters = new ArrayList<MatcherFilter<byte[]>>();

	private ArrayList<MatcherFilter<String>> stringFilters = new ArrayList<MatcherFilter<String>>();
	private ArrayList<MatcherFilter<Document>> documentFilters = new ArrayList<MatcherFilter<Document>>();
	private final Map<String, ResourceItem> cached = new HashMap<String, ResourceItem>();
	private ThreadLocal<ResourceItem> currentItem = new ThreadLocal<ResourceItem>();

	public void addByteFilter(String pattern, FilterPlugin<byte[]> filter) {
		streamFilters.add(new MatcherFilter<byte[]>(pattern, filter));
	}

	public void addStringFilter(String pattern, FilterPlugin<String> filter) {
		stringFilters.add(new MatcherFilter<String>(pattern, filter));
	}

	public void addDocumentFilter(String pattern, FilterPlugin<Document> filter) {
		documentFilters.add(new MatcherFilter<Document>(pattern, filter));
	}

	public File getRoot() {
		return new File(config.getRoot());
	}

	public void addRelation(String relationPath) {
		ResourceItem current = currentItem.get();
		if (current == null) {
			log.error("addRelation 不能在插件外调用。否则无法找到被添加依赖的源路径。");
		} else {
			current.relations.add(relationPath);
		}
	}

	public String getEncoding(String path) {
		return config.getFeatureMap(path).get(ParseContext.FEATURE_ENCODING);
	}

	public byte[] getRawBytes(String path) throws IOException {
		InputStream in = ParseUtil.openStream(config.getRoot().resolve(
				path.substring(1)));
		return loadAndClose(in);
	}

	public byte[] getFilteredBytes(String path) throws IOException {
		try {
			return getFilteredContent(path, byte[].class).data;// ,streamFilters,stringFilters,documentFilters);
		} catch (SAXException e) {
			throw new IOException(e);
		}
	}

	public String getFilteredText(String path) throws IOException {
		try {
			ResourceItem item = getFilteredContent(path, String.class);
			String text = item.text;// ,streamFilters,stringFilters,documentFilters);
			if (text == null) {
				text = ParseUtil.loadTextAndClose(new ByteArrayInputStream(
						item.data), getEncoding(path));
			}
			item.text = text;
			return text;
		} catch (SAXException e) {
			throw new IOException(e);
		}
	}

	public Document getFilteredDocument(String path) throws IOException,
			SAXException {
		return getFilteredContent(path, Document.class).dom;// ,streamFilters,stringFilters,documentFilters);
	}

	/**
	 * 注意: getFilteredContent(path,String.class):
	 * 如果text没有被getFilterdDocument初始化，也没有相关的文本filter,返回null
	 * 但是：getFilterdText，能有效返回数据
	 * 
	 * @param <T>
	 * @param path
	 * @param type
	 * @return
	 * @throws IOException
	 * @throws SAXException
	 */
	@SuppressWarnings("unchecked")
	private <T> ResourceItem getFilteredContent(String path, Class<T> type
	// ,List<FilterPlugin<byte[]>> streamFilters,
	// List<FilterPlugin<String>> stringFilters,
	// List<FilterPlugin<Document>> documentFilters
	) throws IOException, SAXException {
		ResourceItem res = resource(path);
		ResourceItem old = currentItem.get();
		try {
			currentItem.set(res);
			if (res.data == null) {// byte[]
				byte[] data = getRawBytes(path);
				for (MatcherFilter<byte[]> filter : streamFilters) {
					if (filter.match(path)) {
						data = filter.doFilter(path, data);
					}
				}
				res.data = data;
			}
			if (type == byte[].class) {
				return res;
			}

			if (type == String.class) {
				if (res.text == null) {// string
					String text = null;
					for (MatcherFilter<String> filter : stringFilters) {
						if (filter.match(path)) {
							if (text == null) {
								text = ParseUtil.loadTextAndClose(
										new ByteArrayInputStream(res.data),
										getEncoding(path));
							}
							text = filter.doFilter(path, text);
						}
					}
					res.text = text;
				}
				return res;
			} else {
				if (res.dom == null) {
					String text = ParseUtil
							.loadXMLTextAndClose(new ByteArrayInputStream(
									res.data));
					// check xml instruction utf-8
					for (MatcherFilter<String> filter : stringFilters) {
						if (filter.match(path)) {
							text = filter.doFilter(path, text);
						}
					}
					if (res.text == null) {
						res.text = text;
					}
					// 没有默认的xml正规化
					Document doc = ParseUtil.loadXMLBySource(text, "lite:///"
							+ path);
					for (FilterPlugin<Document> filter : documentFilters) {
						doc = filter.doFilter(path, doc);
					}
					res.dom = doc;
				}
				return res;
			}
		} finally {
			currentItem.set(old);
			res.lastModified = System.currentTimeMillis();
		}
	}

	private byte[] loadAndClose(InputStream in) throws IOException {
		byte[] data = new byte[1024];
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int b;
		while ((b = in.read(data)) >= 0) {
			out.write(data, 0, b);
		}
		in.close();
		return out.toByteArray();
	}

	private ResourceItem resource(String path) {
		ResourceItem item = cached.get(path);
		if (item != null) {
			long lastModified = new File(root, path).lastModified();
			for (String path2 : item.relations) {
				File f = new File(root, path2);
				if (!f.exists()) {
					item = null;
					break;
				}
				lastModified = Math.max(lastModified, f.lastModified());
			}
			if (lastModified > item.lastModified) {
				item = null;
			}
		}
		if (item == null) {
			item = new ResourceItem();
			item.path = path;
			cached.put(path, item);
		}
		return item;
	}

	private static class MatcherFilter<T> implements FilterPlugin<T> {
		FilterPlugin<T> base;
		private URLMatcher matcher;

		MatcherFilter(String pattern, FilterPlugin<T> base) {
			this.matcher = URLMatcher.createMatcher(pattern);
			this.base = base;
		}

		public boolean match(String url) {
			return matcher.match(url);
		}

		public T doFilter(String path, T in) {
			return base.doFilter(path, in);
		}

		public ResourceManager getFactory() {
			return base.getFactory();
		}

	}

	private static class ResourceItem {
		ArrayList<String> relations = new ArrayList<String>();
		// List<FilterPlugin<byte[]>> streamFilters = new
		// ArrayList<FilterPlugin<byte[]>>();
		// List<FilterPlugin<String>> stringFilters = new
		// ArrayList<FilterPlugin<String>>();
		// List<FilterPlugin<Document>> documentFilters = new
		// ArrayList<FilterPlugin<Document>>();
		@SuppressWarnings("unused")
		String path;
		byte[] data;
		String text;
		Document dom;
		long lastModified;
		public String hash;
	}

	public String getContentHash(String path) {
		try {
			ResourceItem item = getFilteredContent(path, String.class);
			if (item.hash == null) {
				hash(item.data);
				String text = item.text;
				if (text == null) {
					item.hash = hash(item.data);
				} else {
					item.hash = hash(text.getBytes());
				}
			}
			return item.hash;
		} catch (Exception e) {
			log.warn("计算hash值失败：" + path);
		}

		return null;
	}

	private static String hash(byte[] in) throws NoSuchAlgorithmException,
			IOException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.reset();
		md.update(in);
		long sum = 0;
		byte[] bs = md.digest();
		for (int i = 0; i < bs.length; i++) {
			int b = (0xFF & bs[i]) << (i % 8 * 8);
			sum += b;
		}
		String hash = Long.toString(Math.abs(sum), Character.MAX_RADIX);
		return hash;
	}
}