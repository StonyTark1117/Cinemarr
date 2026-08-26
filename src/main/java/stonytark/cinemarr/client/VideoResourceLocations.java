package stonytark.cinemarr.client;

import net.minecraft.resources.ResourceLocation;

final class VideoResourceLocations {
    static ResourceLocation create(String namespace,String path){return ResourceLocation.fromNamespaceAndPath(namespace,path);}
    private VideoResourceLocations(){}
}
