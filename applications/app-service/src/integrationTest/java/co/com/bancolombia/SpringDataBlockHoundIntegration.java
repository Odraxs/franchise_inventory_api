package co.com.bancolombia;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * Allows only the one library method below to block on a Netty or R2DBC thread. Registered through
 * META-INF/services, so blockhound-junit-platform installs it with BlockHound.
 */
public class SpringDataBlockHoundIntegration implements BlockHoundIntegration {

    /** Spring Data reads a projection's class file from its jar the first time a query maps rows to it. */
    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside(
                "org.springframework.data.projection.DefaultProjectionInformation$PropertyDescriptorSource",
                "getMetadata");
    }
}
