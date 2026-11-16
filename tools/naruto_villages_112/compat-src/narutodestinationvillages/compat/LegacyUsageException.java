package narutodestinationvillages.compat;
public final class LegacyUsageException extends RuntimeException {
 public final Object[] arguments;
 public LegacyUsageException(String message,Object...args){super(message);arguments=args.clone();}
}
