package tfd.pay.common.config;

import org.junit.jupiter.api.Test;
import tfd.pay.common.model.NodeId;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterConfigTest {

    @Test
    void loadsExample() throws Exception {
        try (var in = getClass().getResourceAsStream("/cluster.test.yaml")) {
            ClusterConfig c = ClusterConfig.load(in);
            assertThat(c.n()).isEqualTo(4);
            assertThat(c.quorums().readyToDeliver()).isEqualTo(3);
            assertThat(c.hmacKey(new NodeId(2), new NodeId(1))).hasSize(32);
            assertThat(c.node(new NodeId(3)).clientPort()).isEqualTo(8003);
            assertThat(c.wallet().submitFanout()).isEqualTo(2);
        }
    }

    @Test
    void rejectsNLessThan3fPlus1() {
        String yaml = "n: 3\nf: 1\nnodes: []\nhmacKeys: {}\ngenesis: []\nwallet: {submitFanout: 2, resendIntervalMs: 1000}\n";
        assertThatThrownBy(() -> ClusterConfig.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
