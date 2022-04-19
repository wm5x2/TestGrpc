package jp.co.www.resolver;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;

import io.grpc.NameResolver;
import io.grpc.NameResolver.Args;
import io.grpc.NameResolverProvider;
import io.grpc.internal.GrpcUtil;

@Component
public class K8sClientNameResolverProvider2 extends NameResolverProvider {
	public static final String SCHEME = "kubernetes";

	@Autowired
	private K8sClientNameResolver2 k8sClientNameResolver;

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

				k8sClientNameResolver.init(parts[1], parts[2], port, args,
						GrpcUtil.TIMER_SERVICE, GrpcUtil.SHARED_CHANNEL_EXECUTOR);
				return k8sClientNameResolver;
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

	public void shutdown() {
		if (k8sClientNameResolver != null) {
			k8sClientNameResolver.shutdown();
		}
	}

}
