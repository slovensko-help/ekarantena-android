package sk.nczi.covid19;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.lang.IllegalArgumentException;
import java.lang.IllegalStateException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Security {
	private static final String PROVIDER = "AndroidKeyStore";

	public static byte[] toByteArray(BigInteger what, int bits) {
		byte[] raw = what.toByteArray();
		int bytes = (bits + 7) / 8;
		if (raw.length < bytes) {
			byte[] result = new byte[bytes];
			System.arraycopy(raw, 0, result, bytes - raw.length, raw.length);
			return result;
		}
		if (bytes < raw.length) {
			byte[] result = new byte[bytes];
			System.arraycopy(raw, raw.length - bytes, result, 0, bytes);
			return result;
		}
		return raw;
	}

	public static byte[] concatenate(byte[]... arrays) {
		int len = 0;
		for (byte[] array : arrays) {
			if (array == null)
				continue;
			len += array.length;
		}
		byte[] out = new byte[len];
		int offset = 0;
		for (byte[] array : arrays) {
			if (array == null || array.length == 0)
				continue;
			System.arraycopy(array, 0, out, offset, array.length);
			offset += array.length;
		}
		return out;
	}

	public static byte[] toX962Uncompressed(ECPublicKey publicKey) {
		ECPoint point = publicKey.getW();
		if (point.equals(ECPoint.POINT_INFINITY)) {
			return new byte[]{0};
		}
		byte[] x = toByteArray(point.getAffineX(), 256);
		byte[] y = toByteArray(point.getAffineY(), 256);
		return concatenate(new byte[]{0x04}, x, y);
	}

	public static KeyStore.PrivateKeyEntry getPrivateKey(String keyAlias) {
		try {
			KeyStore ks = KeyStore.getInstance(PROVIDER);
			ks.load(null);
			KeyStore.Entry entry = ks.getEntry(keyAlias, null);
			return (entry instanceof KeyStore.PrivateKeyEntry ? (KeyStore.PrivateKeyEntry) entry : null);
		} catch (Exception e) {
			App.log("Security.getPrivateKey " + e);
			return null;
		}
	}

	/**
	 * Generates a new key-pair, storing the private key in key store and returning formatted public key.
	 *
	 * @param context
	 * @param keyAlias
	 * @return Base64-formatted public key
	 */
	public static String generateKeyPair(Context context, String keyAlias) {
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER);
			ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
			AlgorithmParameterSpec spec;
			if (Build.VERSION.SDK_INT < 23) {
				spec = new KeyPairGeneratorSpec.Builder(context)
						.setAlias(keyAlias)
						.setAlgorithmParameterSpec(ecSpec)
						.build();
			} else {
				spec = new KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
						.setAlgorithmParameterSpec(ecSpec)
						.setDigests(KeyProperties.DIGEST_SHA256)
						.build();
			}
			keyPairGenerator.initialize(spec, new SecureRandom());
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			return Base64.encodeToString(toX962Uncompressed((ECPublicKey) keyPair.getPublic()), Base64.NO_WRAP);
		} catch (Exception e) {
			App.log("Security.generateKeyPair " + e);
			return null;
		}
	}

	public static String sign(String data, PrivateKey privateKey) {
		try {
			Signature s = Signature.getInstance("SHA256withECDSA");
			// Initialize Signature using specified private key
			s.initSign(privateKey);
			// Sign the data, store the result as a Base64 encoded String.
			s.update(data.getBytes());
			byte[] signature = s.sign();
			return Base64.encodeToString(signature, Base64.NO_WRAP);
		} catch (Exception e) {
			App.log("Security.sign " + e);
			return null;
		}
	}

	public static String hotp(byte[] secret, int challenge) {
		try {
			Hotp hotp = new Hotp(secret, 6, "HmacSHA256");
			return hotp.create(challenge);
		} catch (Exception e) {
			App.log("Security.hotp " + e);
			return null;
		}
	}

	public static byte[] hexToBytes(String s) {
		int len = s.length();
		if ((len % 2) != 0) {
			throw new IllegalArgumentException();
		}
		byte[] data = new byte[len / 2];

		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}

		return data;
	}

	private static byte[][] loadHashes(Context context, int resourceId) {
		byte[][] hashes;
		try {
			InputStream forbidden = context.getResources().openRawResource(resourceId);
			BufferedReader reader = new BufferedReader(new InputStreamReader(forbidden));
			String line;
			List<String> lines = new LinkedList<>();
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			reader.close();
			hashes = new byte[lines.size()][];
			for (int i = 0; i < lines.size(); ++i) {
				hashes[i] = hexToBytes(lines.get(i));
			}
		} catch (IOException | Resources.NotFoundException ioe) {
			return null;
		}
		return hashes;
	}

	public static List<String> allowedHashes(Context context, List<String> badApps, int allowedResourceId) {
		byte[][] allowedApps = loadHashes(context, allowedResourceId);
		List<String> result = new LinkedList<>();
		if (allowedApps == null) {
			return result;
		}
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException nsae) {
			return result;
		}
		for (String packageName : badApps) {
			md.update(packageName.getBytes(StandardCharsets.US_ASCII));
			byte[] digest = md.digest();
			md.reset();
			for (byte[] allowedHash : allowedApps) {
				if (Arrays.equals(digest, allowedHash)) {
					result.add(packageName);
				}
			}
		}
		return result;
	}

	public static List<String> testPackageHashes(Context context, int resourceId) {
		List<String> found = new LinkedList<>();
		byte[][] badApps = loadHashes(context, resourceId);
		if (badApps == null) {
			return found;
		}
		PackageManager pm = context.getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException nsae) {
			return found;
		}

		for (ApplicationInfo info : packages) {
			md.update(info.packageName.getBytes(StandardCharsets.US_ASCII));
			byte[] result = md.digest();
			md.reset();
			for (byte[] badHash : badApps) {
				if (Arrays.equals(result, badHash)) {
					found.add(info.packageName);
				}
			}

		}
		return found;
	}

	public static List<String> testPackagesMock(Context context) {
		List<String> found = new LinkedList<>();
		PackageManager pm = context.getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		for (ApplicationInfo info : packages) {
			boolean isSystemPackage = ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
			try {
				if (!isSystemPackage && hasAppPermission(pm, info.packageName, "android.permission.ACCESS_MOCK_LOCATION")) {
					found.add(info.packageName);
				}
			} catch (PackageManager.NameNotFoundException e) {
				/* ignored */
			}
		}
		return found;
	}

	public static boolean hasAppPermission(PackageManager pm, String app, String permission) throws PackageManager.NameNotFoundException {
		PackageInfo packageInfo = pm.getPackageInfo(app, PackageManager.GET_PERMISSIONS);
		if (packageInfo.requestedPermissions != null) {
			for (String requestedPermission : packageInfo.requestedPermissions) {
				if (requestedPermission.equals(permission)) {
					return true;
				}
			}
		}
		return false;
	}


	/**
	 * Roughly inspired by:
	 * - https://github.com/google/google-authenticator-android/blob/master/java/com/google/android/apps/authenticator/otp/PasscodeGenerator.java
	 * - The RFC: https://tools.ietf.org/html/rfc4226
	 */
	public static class Hotp {

		private byte[] secret;
		private int digits;
		private String type;
		private static final int[] DIGITS = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000};

		Hotp(byte[] secret, int digits, String type) {
			if (digits > 9) {
				throw new IllegalArgumentException("Digits must be <= 9.");
			}
			if (!(type.equals("HmacSHA1") || type.equals("HmacSHA256"))) {
				//Tests use SHA1, production uses SHA256
				throw new IllegalArgumentException("Only SHA1 or SHA256 allowed");
			}
			this.secret = secret.clone();
			this.digits = digits;
			this.type = type;
		}

		public String create(int challenge) throws NoSuchAlgorithmException, InvalidKeyException {
			byte[] counter = ByteBuffer.allocate(8).putLong(challenge).array();

			Mac hmac = Mac.getInstance(this.type);
			SecretKeySpec key = new SecretKeySpec(this.secret, this.type);

			hmac.init(key);
			byte[] hash = hmac.doFinal(counter);

			int offset = hash[hash.length - 1] & 0xF;
			int truncatedHash = hashToInt(hash, offset) & 0x7FFFFFFF;
			int pinValue = truncatedHash % DIGITS[this.digits];
			return padOutput(pinValue);
		}

		private int hashToInt(byte[] bytes, int start) {
			DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes, start, bytes.length - start));
			int val;
			try {
				val = input.readInt();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			return val;
		}

		private String padOutput(int value) {
			StringBuilder result = new StringBuilder(Integer.toString(value));
			for (int i = result.length(); i < this.digits; i++) {
				result.insert(0, "0");
			}
			return result.toString();
		}
	}
}
