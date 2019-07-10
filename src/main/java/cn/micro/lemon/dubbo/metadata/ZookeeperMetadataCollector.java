package cn.micro.lemon.dubbo.metadata;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.metadata.identifier.MetadataIdentifier;

/**
 * Zookeeper Metadata Collector
 *
 * @author lry
 */
@Slf4j
public class ZookeeperMetadataCollector implements MetadataCollector {

    private CuratorFramework client;
    private String root;
    private final static String METADATA_NODE_NAME = "service.data";
    private final static String DEFAULT_ROOT = "dubbo";

    @Override
    public void initialize(URL url) {
        String group = url.getParameter(CommonConstants.GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(CommonConstants.PATH_SEPARATOR)) {
            group = CommonConstants.PATH_SEPARATOR + group;
        }

        this.root = group;
        this.client = CuratorFrameworkFactory.newClient(url.getAddress(),
                new ExponentialBackoffRetry(1000, 3));
        client.start();
    }

    @Override
    public String getProviderMetaData(MetadataIdentifier metadataIdentifier) {
        try {
            String path = getNodePath(metadataIdentifier);
            if (client.checkExists().forPath(path) == null) {
                return null;
            }

            return new String(client.getData().forPath(path));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    private String getNodePath(MetadataIdentifier metadataIdentifier) {
        return toRootDir() + metadataIdentifier.getUniqueKey(MetadataIdentifier.KeyTypeEnum.PATH) +
                CommonConstants.PATH_SEPARATOR + METADATA_NODE_NAME;
    }

    private String toRootDir() {
        if (root.equals(CommonConstants.PATH_SEPARATOR)) {
            return root;
        }

        return root + CommonConstants.PATH_SEPARATOR;
    }

}
