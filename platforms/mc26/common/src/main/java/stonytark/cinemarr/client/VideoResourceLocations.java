package stonytark.cinemarr.client;

import net.minecraft.resources.Identifier;

final class VideoIdentifiers {
    static Identifier create(String namespace,String path){return Identifier.fromNamespaceAndPath(namespace,path);}
    private VideoIdentifiers(){}
}
