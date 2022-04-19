package jp.co.www.resolver;

import java.net.URI;

import com.google.common.base.Preconditions;

import io.grpc.NameResolver;
import io.grpc.NameResolver.Args;
import io.grpc.NameResolverProvider;

public class K8sClientNameResolverProvider extends NameResolverProvider {
	public static final String SCHEME = "kubernetes";

	@Override
	protected boolean isAvailable() {
		return true;
	}

	@Override
	protected int priority() {
		return 5;
	}

	@Override
	public NameResolver newNameResolver(URI targetUri, Args args) {

		if (SCHEME.equals(targetUri.getScheme())) {
			String targetPath = Preconditions.checkNotNull(targetUri.getPath(), "targetPath");
			Preconditions.checkArgument(targetPath.startsWith("/"),
					"the path component (%s) of the target (%s) must start with '/'", targetPath, targetUri);

			String[] parts = targetPath.split("/");
			if (parts.length != 4) {
				throw new IllegalArgumentException("Must be formatted like kubernetes:///{namespace}/{service}/{port}");
			}

			try {
				int port = Integer.valueOf(parts[3]);

				return new K8sClientNameResolver(parts[1], parts[2], port);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Unable to parse port number", e);
			}

		} else {
			return null;
		}
	}

	@Override
	public String getDefaultScheme() {
		return SCHEME;
	}

}
