/*
 * Copyright 2011-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package piuk.blockchain.android.ui;

import java.math.BigInteger;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;

import piuk.EventListeners;
import piuk.MyTransaction;
import piuk.blockchain.android.AddressBookProvider;
import piuk.blockchain.android.R;
import piuk.blockchain.android.WalletApplication;
import android.app.Activity;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;


/**
 * @author Andreas Schildbach
 */
public final class WalletTransactionsFragment extends ListFragment {
	private WalletApplication application;
	private Activity activity;
	private ArrayAdapter<MyTransaction> adapter;
	private final Handler handler = new Handler();

	private EventListeners.EventListener eventListener = new EventListeners.EventListener() {
		@Override
		public void onCoinsSent(final MyTransaction tx, final long result) {
			handler.post(new Runnable() {
				@Override
				public void run() {		
					setAdapterContent();
					
					adapter.notifyDataSetChanged();
				}
			});			
		};

		@Override
		public void onCoinsReceived(final MyTransaction tx, final long result) {
			handler.post(new Runnable() {
				@Override
				public void run() {	
					setAdapterContent();

					adapter.notifyDataSetChanged();
				}
			});			
		};

		@Override
		public void onWalletDidChange() {				
			handler.post(new Runnable() {

				@Override
				public void run() {
					setAdapterContent();

					adapter.notifyDataSetChanged();
				}
			});
		}
	};

	private final static String KEY_MODE = "mode";

	private final ContentObserver contentObserver = new ContentObserver(handler) {
		@Override
		public void onChange(final boolean selfChange) {
			try {
				setAdapterContent();

				adapter.notifyDataSetChanged();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
		application = (WalletApplication) activity.getApplication();
	}

	public void initAdapter() {
		adapter = new ArrayAdapter<MyTransaction>(activity, 0) {
			final DateFormat dateFormat = android.text.format.DateFormat
					.getDateFormat(activity);
			final DateFormat timeFormat = android.text.format.DateFormat
					.getTimeFormat(activity);
			final int colorSignificant = getResources().getColor(
					R.color.significant);
			final int colorInsignificant = getResources().getColor(
					R.color.insignificant);
			final int colorSent = getResources().getColor(
					R.color.color_sent);
			final int colorReceived = getResources().getColor(
					R.color.color_received);

			@Override
			public View getView(final int position, View row,
					final ViewGroup parent) {
				if (row == null)
					row = getLayoutInflater(null).inflate(
							R.layout.transaction_row, null);

				final MyTransaction tx = getItem(position);
				final TransactionConfidence confidence = tx.getConfidence();
				final ConfidenceType confidenceType = confidence
						.getConfidenceType();

				try {
					final BigInteger value = tx.getResult();
					final boolean sent = value.signum() < 0;

					final int textColor;
					if (confidenceType == ConfidenceType.NOT_SEEN_IN_CHAIN) {
						textColor = colorInsignificant;
					} else if (confidenceType == ConfidenceType.BUILDING) {

						textColor = colorSignificant;
					} else if (confidenceType == ConfidenceType.NOT_IN_BEST_CHAIN) {
						textColor = colorSignificant;
					} else if (confidenceType == ConfidenceType.DEAD) {
						textColor = Color.RED;
					} else {
						textColor = colorInsignificant;
					}

					final String address;
					if (sent)
						if (tx.getOutputs().size() == 0)
							address = "Unknown";
						else
							address = tx.getOutputs().get(0)
							.getScriptPubKey().getToAddress()
							.toString();
					else if (tx.getInputs().size() == 0)
						address = "Generation";
					else
						address = tx.getInputs().get(0).getFromAddress()
						.toString();

					String label = null;
					if (tx instanceof MyTransaction
							&& ((MyTransaction) tx).getTag() != null)
						label = ((MyTransaction) tx).getTag();
					else
						label = AddressBookProvider.resolveLabel(
								activity.getContentResolver(), address);

					final TextView rowTime = (TextView) row
							.findViewById(R.id.transaction_row_time);
					final Date time = tx.getUpdateTime();
					rowTime.setText(time != null ? (DateUtils.isToday(time
							.getTime()) ? timeFormat.format(time)
									: dateFormat.format(time)) : null);
					rowTime.setTextColor(textColor);

					final TextView rowLabel = (TextView) row
							.findViewById(R.id.transaction_row_address);
					rowLabel.setTextColor(textColor);
					rowLabel.setText(label != null ? label : address);
					rowLabel.setTypeface(label != null ? Typeface.DEFAULT
							: Typeface.MONOSPACE);

					final CurrencyAmountView rowValue = (CurrencyAmountView) row
							.findViewById(R.id.transaction_row_value);
					rowValue.setCurrencyCode(null);
					rowValue.setAmountSigned(true);
					rowValue.setTextColor(textColor);
					rowValue.setAmount(value);

					if (sent) {
						rowValue.setTextColor(colorSent);
					} else {
						rowValue.setTextColor(colorReceived);
					}

					return row;
				} catch (final ScriptException x) {
					throw new RuntimeException(x);
				}
			}
		};

		setAdapterContent();
		
		setListAdapter(adapter);
	}
	
	public void setAdapterContent() {
		
		System.out.println("setAdapterContent()");
		
		adapter.clear();
		
		List<MyTransaction> transactions = application.getRemoteWallet().getMyTransactions();

		for (MyTransaction transaction : transactions) {
			adapter.add(transaction);
		}
	}

	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		EventListeners.addEventListener(eventListener);

		initAdapter();

		activity.getContentResolver().registerContentObserver(AddressBookProvider.CONTENT_URI, true, contentObserver);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onViewCreated(final View view,
			final Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		registerForContextMenu(getListView());
	}

	@Override
	public void onDestroy() {
		activity.getContentResolver().unregisterContentObserver(contentObserver);
		
		EventListeners.removeEventListener(eventListener);

		super.onDestroy();
	}

	@Override
	public void onListItemClick(final ListView l, final View v,
			final int position, final long id) {
		final MyTransaction tx = adapter.getItem(position);
		editAddress(tx);
	}

	// workaround http://code.google.com/p/android/issues/detail?id=20065
	private static View lastContextMenuView;

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v,
			final ContextMenuInfo menuInfo) {
		activity.getMenuInflater().inflate(
				R.menu.wallet_transactions_context, menu);

		lastContextMenuView = v;
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item) {
		final AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
				.getMenuInfo();
		final ListAdapter adapter = ((ListView) lastContextMenuView)
				.getAdapter();
		final MyTransaction tx = (MyTransaction) adapter.getItem(menuInfo.position);

		switch (item.getItemId()) {
		case R.id.wallet_transactions_context_edit_address:
			editAddress(tx);
			return true;

			/*
			 * case R.id.wallet_transactions_context_show_transaction:
			 * TransactionActivity.show(activity, tx); return true;
			 */
		default:
			return false;
		}
	}

	private void editAddress(final MyTransaction tx) {
		try {
			final boolean sent = tx.getResult().signum() < 0;

			Address address = null;
			if (sent) {
				if (tx.getOutputs().size() == 0)
					return;

				address = tx.getOutputs().get(0).getScriptPubKey()
						.getToAddress();
			} else {
				if (tx.getInputs().size() == 0)
					return;

				address = tx.getInputs().get(0).getFromAddress();
			}

			EditAddressBookEntryFragment.edit(getFragmentManager(),
					address.toString());
		} catch (final ScriptException x) {
			// ignore click
			x.printStackTrace();
		}
	}
}