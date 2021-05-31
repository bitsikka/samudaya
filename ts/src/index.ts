import PeerId from "peer-id";
import {
  ChatMessage,
  getStatusFleetNodes,
  Environment,
  StoreCodec,
  Waku,
  WakuMessage,
  Direction,
} from "js-waku";

async function retrieveStoreMessages(
  waku: Waku,
  peerId: PeerId,
  ChatContentTopic: string,
  archivedMessagesCallback: (value: ChatMessage[]) => void
): Promise<number> {
  const callback = (wakuMessages: WakuMessage[]): void => {
    const messages = wakuMessages
      .map((wakuMsg) => wakuMsg.payload)
      .filter((payload) => !!payload)
      .map((payload) => ChatMessage.decode(payload as Uint8Array));
    archivedMessagesCallback(messages);
  };

  const res = await waku.store.queryHistory({
    peerId,
    contentTopics: [ChatContentTopic],
    pageSize: 5,
    callback,
  });

  return res ? res.length : 0;
}

export default async function useStatusStream(
  ChatContentTopic: string,
  archivedMessageHandler: (value: any) => void
): Promise<Waku> {
  const stateWaku = await Waku.create({
    config: {
      pubsub: {
        enabled: true,
        emitSelf: true,
      },
    },
  });

  const nodes = await getStatusFleetNodes(Environment.Prod);
  await Promise.all(
    nodes.map((addr) => {
      stateWaku.dial(addr);
    })
  );

  const handleProtocolChange = async (
    waku: Waku,
    archivedMessageCallback: (value: any) => void,
    { peerId, protocols }: { peerId: PeerId; protocols: string[] }
  ) => {
    if (protocols.includes(StoreCodec)) {
      console.log(`${peerId.toB58String()}: retrieving archived messages}`);
      try {
        const length = await retrieveStoreMessages(
          waku,
          peerId,
          ChatContentTopic,
          archivedMessageCallback
        );
        console.log(`${peerId.toB58String()}: messages retrieved:`, length);
      } catch (e) {
        console.log(
          `${peerId.toB58String()}: error encountered when retrieving archived messages`,
          e
        );
      }
    }
  };

  stateWaku.libp2p.peerStore.on(
    "change:protocols",
    handleProtocolChange.bind({}, stateWaku, archivedMessageHandler)
  );

  return stateWaku;
}
